package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.simulator.SimpleSimulator
import org.tmt.tcs.mcs.MCShcd.workers.{
  DatumCmdActor,
  FollowCmdActor,
  PointCmdActor,
  PointDemandCmdActor,
  ShutdownCmdActor,
  StartupCmdActor
}

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
  private val log                        = loggerFactory.getLogger
  private val simulator: SimpleSimulator = SimpleSimulator.create(loggerFactory)
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {

    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("Starting MCS HCD")
        val startupCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(StartupCmdActor.create(commandResponseManager, simulator, loggerFactory), "StartupCmdActor")
        startupCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.SHUTDOWN => {
        log.info("ShutDown MCS HCD")
        val shutDownCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(ShutdownCmdActor.create(commandResponseManager, simulator, loggerFactory), "ShutdownCmdActor")
        shutDownCmdActor ! controlCommand
        Behavior.stopped
      }
      case Commands.POINT => {
        log.debug(s"handling point command: ${controlCommand}")
        val pointCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(PointCmdActor.create(commandResponseManager, simulator, loggerFactory), "PointCmdActor")
        pointCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.POINT_DEMAND => {
        log.debug(s"handling pointDemand command: ${controlCommand}")
        val pointDemandCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(PointDemandCmdActor.create(commandResponseManager, simulator, loggerFactory), "PointDemandCmdActor")
        pointDemandCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.DATUM => {
        log.info("Received datum command in HCD commandHandler")
        val datumCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(DatumCmdActor.create(commandResponseManager, simulator, loggerFactory), "DatumCmdActor")
        datumCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.FOLLOW => {
        log.info("Received follow command in HCD commandHandler")
        val followCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(FollowCmdActor.create(commandResponseManager, simulator, loggerFactory), name = "FollowCmdActor")
        followCmdActor ! controlCommand
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
