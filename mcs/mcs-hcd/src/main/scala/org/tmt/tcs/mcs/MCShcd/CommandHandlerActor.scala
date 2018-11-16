package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
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

import scala.concurrent.ExecutionContextExecutor
import csw.services.command.CommandResponseManager
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.{submitCommand, ImmediateCommand, ImmediateCommandResponse, SimulatorMode}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

sealed trait HCDCommandMessage
object HCDCommandMessage {
  case class ImmediateCommand(sender: ActorRef[HCDCommandMessage], controlCommand: ControlCommand) extends HCDCommandMessage
  case class ImmediateCommandResponse(commandResponse: CommandResponse)                            extends HCDCommandMessage
  case class submitCommand(controlCommand: ControlCommand)                                         extends HCDCommandMessage
  case class SimulatorMode(controlCommand: ControlCommand)                                         extends HCDCommandMessage

}
object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   lifeCycleActor: ActorRef[LifeCycleMessage],
                   zeroMQProtoActor: ActorRef[ZeroMQMessage],
                   simpleSimActor: ActorRef[SimpleSimMsg],
                   simulatorMode: String,
                   loggerFactory: LoggerFactory): Behavior[HCDCommandMessage] =
    Behaviors.setup(
      ctx =>
        CommandHandlerActor(ctx,
                            commandResponseManager,
                            lifeCycleActor,
                            zeroMQProtoActor,
                            simpleSimActor,
                            simulatorMode,
                            loggerFactory)
    )
}

/*
This actor acts as simple Protocol for HCD commands, it simply sleeps the current thread and updates
command responses with completed messages
 */
case class CommandHandlerActor(ctx: ActorContext[HCDCommandMessage],
                               commandResponseManager: CommandResponseManager,
                               lifeCycleActor: ActorRef[LifeCycleMessage],
                               zeroMQProtoActor: ActorRef[ZeroMQMessage],
                               simpleSimActor: ActorRef[SimpleSimMsg],
                               simulatorMode: String,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[HCDCommandMessage] {
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  override def onMessage(cmdMessage: HCDCommandMessage): Behavior[HCDCommandMessage] = {

    cmdMessage match {
      case msg: ImmediateCommand => {
        processImmediateCommand(msg)
      }
      case msg: submitCommand => {
        processSubmitCommand(msg)
      }
    }
  }
  private def processImmediateCommand(immediateCommand: ImmediateCommand): Behavior[HCDCommandMessage] = {
    immediateCommand.controlCommand.commandName.name match {

      case Commands.FOLLOW => {
        log.info("Received follow command in HCD commandHandler")
        val followCmdActor: ActorRef[ImmediateCommand] =
          ctx.spawn(FollowCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
                    name = "FollowCmdActor")
        followCmdActor ! immediateCommand
        Behavior.same
      }
    }
  }
  private def processSubmitCommand(cmdMessage: submitCommand): Behavior[HCDCommandMessage] = {

    cmdMessage.controlCommand.commandName.name match {

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
          ctx.spawn(
            StartupCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
            "StartupCmdActor"
          )
        startupCmdActor ! cmdMessage.controlCommand
        Behavior.same
      }
      case Commands.SHUTDOWN => {
        log.info("ShutDown MCS HCD")
        val shutDownCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(
            ShutdownCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
            "ShutdownCmdActor"
          )
        shutDownCmdActor ! cmdMessage.controlCommand
        Behavior.stopped
      }
      case Commands.POINT => {
        log.debug(s"handling point command: ${cmdMessage.controlCommand}")
        val pointCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(PointCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
                    "PointCmdActor")
        pointCmdActor ! cmdMessage.controlCommand
        Behavior.same
      }
      case Commands.POINT_DEMAND => {
        log.debug(s"handling pointDemand command: ${cmdMessage.controlCommand}")
        val pointDemandCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(
            PointDemandCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
            "PointDemandCmdActor"
          )
        pointDemandCmdActor ! cmdMessage.controlCommand
        Behavior.same
      }
      case Commands.DATUM => {
        log.info("Received datum command in HCD commandHandler")
        val datumCmdActor: ActorRef[ControlCommand] =
          ctx.spawn(DatumCmdActor.create(commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory),
                    "DatumCmdActor")
        datumCmdActor ! cmdMessage.controlCommand
        Behavior.same
      }

      case _ => {
        log.error(msg = s"Incorrect command is sent to MCS HCD : ${cmdMessage.controlCommand}")
        Behavior.unhandled
      }
    }
  }
}
