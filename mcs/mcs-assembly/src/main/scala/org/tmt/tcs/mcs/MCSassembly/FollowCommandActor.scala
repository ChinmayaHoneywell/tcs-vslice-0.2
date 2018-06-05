package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
object FollowCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.mutable(ctx => FollowCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
case class FollowCommandActor(ctx: ActorContext[ControlCommand],
                              commandResponseManager: CommandResponseManager,
                              hcdLocation: Option[CommandService],
                              loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[ControlCommand] {
  private val log                = loggerFactory.getLogger
  private val mcsHCDPrefix       = Prefix(Subsystem.MCS, "tmt.tcs.mcs")
  implicit val duration: Timeout = 20 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing Follow command ${controlCommand}")
    val setup = Setup(mcsHCDPrefix, CommandName(Commands.FOLLOW), controlCommand.maybeObsId)
    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submit(setup), 3.seconds)
        log.info(msg = s" updating follow command  ${controlCommand.runId} with response : ${response} ")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        log.info(msg = s"completed follow command execution for command id : $controlCommand.runId")
        this
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        Behavior.unhandled
      }
    }
  }
}
