package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import csw.services.command.CommandResponseManager
object FollowCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => FollowCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for processing of Follow command. It uses commandResponseManager to save or update
command responses
 */
case class FollowCommandActor(ctx: ActorContext[ControlCommand],
                              commandResponseManager: CommandResponseManager,
                              hcdLocation: Option[CommandService],
                              loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log                = loggerFactory.getLogger
  private val mcsHCDPrefix       = Prefix(Subsystem.MCS.toString)
  implicit val duration: Timeout = 20 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing Follow command ${controlCommand}")
    //val setup = Setup(mcsHCDPrefix, CommandName(Commands.FOLLOW), controlCommand.maybeObsId)
    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submitAndSubscribe(controlCommand), 3.seconds)
        log.info(msg = s" updating follow command  ${controlCommand.runId} with response : ${response} ")

        //commandResponseManager.addSubCommand(controlCommand.runId, response.runId)
        //commandResponseManager.updateSubCommand(response.runId, response)
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        log.info(msg = s"completed follow command execution for command id : $controlCommand.runId")
        Behavior.stopped
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        Behavior.unhandled
      }
    }
  }
}
