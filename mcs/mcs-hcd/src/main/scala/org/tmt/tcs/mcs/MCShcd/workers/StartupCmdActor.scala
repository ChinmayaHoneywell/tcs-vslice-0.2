package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}

import csw.messages.commands.{CommandResponse, ControlCommand}

import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.SubsystemManager

object StartupCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             subSystemManager: SubsystemManager,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => StartupCmdActor(ctx, commandResponseManager, subSystemManager, loggerFactory))
}
case class StartupCmdActor(ctx: ActorContext[ControlCommand],
                           commandResponseManager: CommandResponseManager,
                           subsystemManager: SubsystemManager,
                           loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Submitting startup  command with id : ${msg.runId} to simulator")
    /* var simulator: Simulator             = null
    val simulatorTypeParam: Parameter[_] = msg.paramSet.find(msg => msg.keyName == "simulatorType").get
    val simulatorType: String            = simulatorTypeParam.head.asInstanceOf[String]
    if (simulatorType.equals("Simple")) {
      simulator = SimpleSimulator.create(loggerFactory)
    } else {
      simulator = RealSimulator.create(loggerFactory)
    }
    subsystemManager.initialize(config, simulator)*/
    val commandResponse: CommandResponse = subsystemManager.sendCommand(msg)
    log.info(s"Response from simulator for command runID : ${msg.runId} is : ${commandResponse}")

    commandResponseManager.addOrUpdateCommand(msg.runId, commandResponse)
    Behavior.stopped
  }
}
