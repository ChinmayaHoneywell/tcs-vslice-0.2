package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.scaladsl.{CommandResponseManager}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands

object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager, loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.mutable(ctx => CommandHandlerActor(ctx, commandResponseManager, loggerFactory))
}
case class CommandHandlerActor(ctx: ActorContext[ControlCommand],
                               commandResponseManager: CommandResponseManager,
                               loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger

  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("Starting MCS HCD")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))

      }
      case Commands.SHUTDOWN => {
        log.info("ShutDown MCS HCD")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
      }
      case Commands.POINT => {
        log.debug(s"handling point command: ${controlCommand}")

        Thread.sleep(500)

        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
      }
      case Commands.POINT_DEMAND => {
        log.debug(s"handling pointDemand command: ${controlCommand}")

        Thread.sleep(1000)

        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
      }
      case Commands.DATUM => {
        log.info("Received datum command in HCD commandHandler")
        Thread.sleep(1000)
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
      }
      case Commands.FOLLOW => {
        log.info("Received follow command in HCD commandHandler")
        Thread.sleep(1000)
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))

      }
      case _ => {
        log.error(msg = s"Incorrect command is sent to MCS HCD : $controlCommand")
        Behavior.unhandled
      }
    }
    this
  }
}
