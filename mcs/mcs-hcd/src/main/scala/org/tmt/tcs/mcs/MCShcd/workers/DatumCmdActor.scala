package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.SubsystemManager

object DatumCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             subsystemManager: SubsystemManager,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => DatumCmdActor(ctx, commandResponseManager, subsystemManager, loggerFactory))

}
case class DatumCmdActor(ctx: ActorContext[ControlCommand],
                         commandResponseManager: CommandResponseManager,
                         subsystemManager: SubsystemManager,
                         loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting datum command with id : ${msg.runId} to simulator")
    val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    log.info(s"Response from simulator for command runID : ${msg.runId} is : ${commandResponse}")
    commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }
}
