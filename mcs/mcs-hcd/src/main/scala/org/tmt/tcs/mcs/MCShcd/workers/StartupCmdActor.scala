package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import scala.concurrent.Await

object StartupCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => StartupCmdActor(ctx, commandResponseManager, zeroMQProtoActor, loggerFactory))
}
case class StartupCmdActor(ctx: ActorContext[ControlCommand],
                           commandResponseManager: CommandResponseManager,
                           zeroMQProtoActor: ActorRef[ZeroMQMessage],
                           loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting startup  command with id : ${msg.runId} to Protocol")
    /* var Protocol: Simulator             = null
    val simulatorTypeParam: Parameter[_] = msg.paramSet.find(msg => msg.keyName == "simulatorType").get
    val simulatorType: String            = simulatorTypeParam.head.asInstanceOf[String]
    if (simulatorType.equals("Simple")) {
      Protocol = SimpleSimulator.create(loggerFactory)
    } else {
      Protocol = ZeroMQProtocolImpl.create(loggerFactory)
    }
    subsystemManager.initialize(config, Protocol)*/
    //val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    //log.info(s"Response from Protocol for command runID : ${msg.runId} is : ${commandResponse}")

    //commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)

    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        log.info(s"Response from MCS for command runID : ${msg.runId} is : ${x}")
        commandResponseManager.addOrUpdateCommand(msg.runId, x.commandResponse)
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(
          msg.runId,
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
      }
    }
    Behavior.stopped
  }
}
