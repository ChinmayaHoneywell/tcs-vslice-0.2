package org.tmt.tcs.mcs.MCShcd.workers
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.simulator.Simulator

object PointDemandCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             simulator: Simulator,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => PointDemandCmdActor(ctx, commandResponseManager, simulator, loggerFactory))
}
case class PointDemandCmdActor(ctx: ActorContext[ControlCommand],
                               commandResponseManager: CommandResponseManager,
                               simulator: Simulator,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting point demand command with id : ${msg.runId} to simulator")
    val commandResponse: CommandResponse = simulator.submitCommand(msg)
    log.info(s"Response from simulator for command runID : ${msg.runId} is : ${commandResponse}")
    commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }

}
