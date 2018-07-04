package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands

object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager, loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => CommandHandlerActor(ctx, commandResponseManager, loggerFactory))
}
/*
This actor acts as simple simulator for HCD commands, it simply sleeps the current thread and updates
command responses with completed messages
 */
case class CommandHandlerActor(ctx: ActorContext[ControlCommand],
                               commandResponseManager: CommandResponseManager,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger

  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("Starting MCS HCD")
        log.info(msg = s"Successfully started MCS HCD")
        //TODO : connecto to subsystem in above call to commandhandler actor
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case Commands.SHUTDOWN => {
        log.info("ShutDown MCS HCD")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case Commands.POINT => {
        log.debug(s"handling point command: ${controlCommand}")

        Thread.sleep(50)

        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case Commands.POINT_DEMAND => {
        log.debug(s"handling pointDemand command: ${controlCommand}")

        Thread.sleep(100)

        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case Commands.DATUM => {
        log.info("Received datum command in HCD commandHandler")
        Thread.sleep(50)
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case Commands.FOLLOW => {
        log.info("Received follow command in HCD commandHandler")
        Thread.sleep(100)
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, CommandResponse.Completed(controlCommand.runId))
        Behavior.same
      }
      case _ => {
        log.error(msg = s"Incorrect command is sent to MCS HCD : ${controlCommand}")
        Behavior.unhandled
      }
    }
    //this
  }
}
