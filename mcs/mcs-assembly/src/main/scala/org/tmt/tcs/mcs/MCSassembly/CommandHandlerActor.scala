package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.ControlCommand
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.CommandMessage.{submitCommandMsg, updateHCDLocation, GoOfflineMsg, GoOnlineMsg}
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

sealed trait CommandMessage
object CommandMessage {
  case class GoOnlineMsg()                                          extends CommandMessage
  case class GoOfflineMsg()                                         extends CommandMessage
  case class submitCommandMsg(controlCommand: ControlCommand)       extends CommandMessage
  case class updateHCDLocation(hcdLocation: Option[CommandService]) extends CommandMessage

}
object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   isOnline: Boolean,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[CommandMessage] =
    Behaviors.mutable(ctx => CommandHandlerActor(ctx, commandResponseManager, isOnline, hcdLocation, loggerFactory))

}
case class CommandHandlerActor(ctx: ActorContext[CommandMessage],
                               commandResponseManager: CommandResponseManager,
                               isOnline: Boolean,
                               hcdLocation: Option[CommandService],
                               loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[CommandMessage] {
  private val log = loggerFactory.getLogger

  override def onMessage(msg: CommandMessage): Behavior[CommandMessage] = {
    msg match {
      case x: GoOnlineMsg      => CommandHandlerActor.createObject(commandResponseManager, true, hcdLocation, loggerFactory)
      case x: GoOfflineMsg     => CommandHandlerActor.createObject(commandResponseManager, false, hcdLocation, loggerFactory)
      case x: submitCommandMsg => handleSubmitCommand(x)
      case x: updateHCDLocation =>
        CommandHandlerActor.createObject(commandResponseManager, isOnline, x.hcdLocation, loggerFactory)
      case _ => {
        log.error(msg = s" Incorrect command : ${msg} is sent to CommandHandlerActor")
        Behaviors.unhandled
      }
    }
    this
  }
  def handleSubmitCommand(msg: submitCommandMsg): Behavior[CommandMessage] = {
    if (isOnline) {
      msg.controlCommand.commandName.name match {
        case Commands.AXIS              => handleAxisCommand(msg)
        case Commands.DATUM             => handleDatumCommand(msg)
        case Commands.MOVE              => handleMoveCommand(msg)
        case Commands.FOLLOW            => handleFollowCommand(msg)
        case Commands.SERVO_OFF         => handleServoOffCommand(msg)
        case Commands.RESET             => handleResetCommand(msg)
        case Commands.SETDIAGNOSTICS    => handleDiagnosticsCommand(msg)
        case Commands.CANCELPROCESSING  => handleCancelProcessing(msg)
        case Commands.READCONFIGURATION => handleReadConfiguration(msg)
        case Commands.ELEVATIONSTOW     => handleElevationStowCommand(msg)
        case _ => {
          log.error(msg = s"Incorrect command ${msg} is sent to MCS Assembly")
          Behavior.unhandled
        }
      }
    }
    this
  }

  def handleAxisCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Axis command to AxisCommandActor")
    val axisCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(AxisCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "AxisCommandActor")
    axisCommandActor ! msg.controlCommand
  }

  def handleDatumCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Datum command to DatumCommandActor")
    val datumCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(DatumCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "DatumCommandActor")
    datumCommandActor ! msg.controlCommand
  }

  def handleMoveCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Move command to MoveCommandActor")
    val moveCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(MoveCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "MoveCommandActor")
    moveCommandActor ! msg.controlCommand
  }
  def handleFollowCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Follow command to FollowCommandActor")
    val followCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(FollowCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "FollowCommandActor")
    followCommandActor ! msg.controlCommand
  }

  def handleServoOffCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  ServoOff command to ServoCommandActor")
    val servoCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(ServoCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "ServoCommandActor")
    servoCommandActor ! msg.controlCommand
  }

  def handleResetCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Reset command to ResetCommandActor")
    val resetCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(ResetCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "ResetCommandActor")
    resetCommandActor ! msg.controlCommand
  }

  def handleDiagnosticsCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  diagnostics command to DiagnosticsCmdActor")
    val diagnosticsCmdActor: ActorRef[ControlCommand] =
      ctx.spawn(DiagnosticsCmdActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "DiagnosticsCmdActor")
    diagnosticsCmdActor ! msg.controlCommand
  }

  def handleCancelProcessing(msg: submitCommandMsg) = {
    log.info(msg = "Sending cancelprocessing command to CancelCmdActor")
    val cancelCmdActor: ActorRef[ControlCommand] =
      ctx.spawn(CancelCmdActor.createObject(commandResponseManager, hcdLocation, loggerFactory), name = "CancelCmdActor")
    cancelCmdActor ! msg.controlCommand
  }

  def handleReadConfiguration(msg: submitCommandMsg) = {
    log.info(msg = "Sending readBehaviour command to ReadBehaviorCmdActor.")
    val readConfigCmdActor: ActorRef[ControlCommand] = ctx.spawn(
      ReadConfigCmdActor.createObject(commandResponseManager, hcdLocation, loggerFactory),
      name = "ReadConfigurationActor"
    )
    readConfigCmdActor ! msg.controlCommand
  }
  def handleElevationStowCommand(msg: submitCommandMsg): Unit = {
    log.info(msg = "Sending elevation stow command to ElevationStowCommandActor")
    val elevationStowActor: ActorRef[ControlCommand] =
      ctx.spawn(ElevationStowActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "ElevationStowActor")
    elevationStowActor ! msg.controlCommand
  }

}
