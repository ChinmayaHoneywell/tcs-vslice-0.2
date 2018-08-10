package org.tmt.tcs.mcs.MCShcd.workers
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.SubsystemManager
import org.tmt.tcs.mcs.MCShcd.simulator.Simulator

object PointCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             subsystemManager: SubsystemManager,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => PointCmdActor(ctx, commandResponseManager, subsystemManager, loggerFactory))
}
case class PointCmdActor(ctx: ActorContext[ControlCommand],
                         commandResponseManager: CommandResponseManager,
                         subsystemManager: SubsystemManager,
                         loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting point  command with id : ${msg.runId} to simulator")
    val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    log.info(s"Response from simulator for command runID : ${msg.runId} is : ${commandResponse}")
    commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }

}
