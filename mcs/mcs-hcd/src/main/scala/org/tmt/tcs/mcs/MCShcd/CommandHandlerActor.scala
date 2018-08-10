package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.ControlCommand

import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.workers.{
  DatumCmdActor,
  FollowCmdActor,
  PointCmdActor,
  PointDemandCmdActor,
  ShutdownCmdActor,
  StartupCmdActor
}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import akka.actor.typed.scaladsl.AskPattern._
import com.typesafe.config.Config
import csw.services.command.scaladsl.CommandResponseManager

import scala.concurrent.duration._
object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   lifeCycleActor: ActorRef[LifeCycleMessage],
                   subSystemManager: SubsystemManager,
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => CommandHandlerActor(ctx, commandResponseManager, lifeCycleActor, subSystemManager, loggerFactory))
}
/*
This actor acts as simple simulator for HCD commands, it simply sleeps the current thread and updates
command responses with completed messages
 */
case class CommandHandlerActor(ctx: ActorContext[ControlCommand],
                               commandResponseManager: CommandResponseManager,
                               lifeCycleActor: ActorRef[LifeCycleMessage],
                               subSystemManager: SubsystemManager,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {

    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("Starting MCS HCD")

        /* val lifecycleMsg = Await.result(lifeCycleActor ? { ref: ActorRef[LifeCycleMessage] =>
          LifeCycleMessage.GetConfig(ref)
        }, 3.seconds)

        var config: Config = null
        lifecycleMsg match {
          case x: LifeCycleMessage.HCDConfig => {
            config = x.config
          }
        }*/
        val startupCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(StartupCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), "StartupCmdActor")
        startupCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.SHUTDOWN => {
        log.info("ShutDown MCS HCD")
        val shutDownCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(ShutdownCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), "ShutdownCmdActor")
        shutDownCmdActor ! controlCommand
        Behavior.stopped
      }
      case Commands.POINT => {
        log.debug(s"handling point command: ${controlCommand}")
        val pointCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(PointCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), "PointCmdActor")
        pointCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.POINT_DEMAND => {
        log.debug(s"handling pointDemand command: ${controlCommand}")
        val pointDemandCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(PointDemandCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), "PointDemandCmdActor")
        pointDemandCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.DATUM => {
        log.info("Received datum command in HCD commandHandler")
        val datumCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(DatumCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), "DatumCmdActor")
        datumCmdActor ! controlCommand
        Behavior.same
      }
      case Commands.FOLLOW => {
        log.info("Received follow command in HCD commandHandler")
        val followCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(FollowCmdActor.create(commandResponseManager, subSystemManager, loggerFactory), name = "FollowCmdActor")
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
