package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.ActorRefFactory
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.CommandResponse
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.{ImmediateCommand, ImmediateCommandResponse}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import akka.stream.ActorMaterializer

import scala.concurrent.{Await, ExecutionContextExecutor}

object FollowCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             loggerFactory: LoggerFactory): Behavior[ImmediateCommand] =
    Behaviors.setup(ctx => FollowCmdActor(ctx, commandResponseManager, zeroMQProtoActor, loggerFactory))
}
case class FollowCmdActor(ctx: ActorContext[ImmediateCommand],
                          commandResponseManager: CommandResponseManager,
                          zeroMQProtoActor: ActorRef[ZeroMQMessage],
                          loggerFactory: LoggerFactory)
    extends MutableBehavior[ImmediateCommand] {
  private val log: Logger                   = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  override def onMessage(msg: ImmediateCommand): Behavior[ImmediateCommand] = {
    log.info(s"Submitting follow command with id : ${msg.controlCommand.runId} to Protocol")
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    /*implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()*/
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg.controlCommand)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        log.info(s"Response from MCS for command runID : ${msg.controlCommand.runId} is : ${x}")
        msg.sender ! ImmediateCommandResponse(x.commandResponse)
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(
          msg.controlCommand.runId,
          CommandResponse.Error(msg.controlCommand.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
      }
    }
    //val commandResponse: CommandResponse = subsystemManager.sendCommand(msg.controlCommand)

    //commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }

}
