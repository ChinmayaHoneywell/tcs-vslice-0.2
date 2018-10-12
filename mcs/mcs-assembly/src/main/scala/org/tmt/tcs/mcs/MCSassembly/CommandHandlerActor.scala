package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.CommandMessage._
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.util.Timeout
import csw.messages.params.models.{Prefix, Subsystem}
import csw.services.command.CommandResponseManager

sealed trait CommandMessage
object CommandMessage {
  case class GoOnlineMsg()                                                                      extends CommandMessage
  case class GoOfflineMsg()                                                                     extends CommandMessage
  case class submitCommandMsg(controlCommand: ControlCommand)                                   extends CommandMessage
  case class updateHCDLocation(hcdLocation: Option[CommandService])                             extends CommandMessage
  case class ImmediateCommand(sender: ActorRef[CommandMessage], controlCommand: ControlCommand) extends CommandMessage
  case class ImmediateCommandResponse(commandResponse: CommandResponse)                         extends CommandMessage

}
object CommandHandlerActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   isOnline: Boolean,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[CommandMessage] =
    Behaviors.setup(ctx => CommandHandlerActor(ctx, commandResponseManager, isOnline, hcdLocation, loggerFactory))

}
/*
This class acts as a router for commands it rounds each command to individual
command worker actor, it uses commandResponseManager to save and update command responses
 */
case class CommandHandlerActor(ctx: ActorContext[CommandMessage],
                               commandResponseManager: CommandResponseManager,
                               isOnline: Boolean,
                               hcdLocation: Option[CommandService],
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[CommandMessage] {
  import org.tmt.tcs.mcs.MCSassembly.CommandHandlerActor._
  private val log                = loggerFactory.getLogger
  implicit val duration: Timeout = 20 seconds
  private val mcsHCDPrefix       = Prefix(Subsystem.MCS.toString)
  /*
  This function processes goOnline, goOffline,handleSubmit,updateHCDLocation
   command messages

   */
  override def onMessage(msg: CommandMessage): Behavior[CommandMessage] = {
    msg match {
      case x: GoOnlineMsg => {
        createObject(commandResponseManager, true, hcdLocation, loggerFactory)
      }
      case x: GoOfflineMsg => {
        createObject(commandResponseManager, false, hcdLocation, loggerFactory)
      }
      case x: submitCommandMsg => {
        // log.info(msg = s"In commandHandlerActor submitCommandMsg processing command :${x} ")
        handleSubmitCommand(x)
        Behavior.same
      }
      case x: ImmediateCommand => {
        //log.info(msg = s"In commandHandler actor processing immediate command : ${x}")
        handleImmediateCommand(x)
        Behavior.same
      }
      case x: updateHCDLocation => {
        createObject(commandResponseManager, isOnline, x.hcdLocation, loggerFactory)
      }
      case _ => {
        log.error(msg = s" Incorrect command : ${msg} is sent to CommandHandlerActor")
        Behaviors.unhandled
      }
    }

  }
  def handleImmediateCommand(msg: ImmediateCommand): Unit = {

    log.info(s"In CommandHandlerActor processing immediate command : ${msg}")
    msg.controlCommand.commandName.name match {
      case Commands.FOLLOW => {
        handleFollowCommand(msg)
      }
    }
  }
  /*
    This function creates individual actor for each command and delegates
    working to it
   */
  def handleSubmitCommand(msg: submitCommandMsg): Unit = {
    /*log.info(
      msg = s"In commandHandlerActor handleSubmitCommand()" +
      s" function value of hcdLocation is : ${hcdLocation}"
    )*/
    msg.controlCommand.commandName.name match {
      case Commands.STARTUP  => handleStartupCommand(msg)
      case Commands.SHUTDOWN => handleShutDownCommand(msg)

      case Commands.DATUM => handleDatumCommand(msg)
      case Commands.MOVE  => handleMoveCommand(msg)
      case Commands.DUMMY_LONG => {
        commandResponseManager.addOrUpdateCommand(msg.controlCommand.runId, CommandResponse.Completed(msg.controlCommand.runId))
      }
      case _ => {
        log.error(msg = s"Incorrect command : ${msg} is sent to MCS Assembly CommandHandlerActor")
      }
    }
  }
  def handleShutDownCommand(msg: submitCommandMsg): Unit = {
    log.info(msg = s"In assembly command Handler Actor submitting shutdown command")
    val setup = Setup(mcsHCDPrefix, CommandName(Commands.SHUTDOWN), msg.controlCommand.maybeObsId)
    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submit(setup), 3.seconds)
        log.info(msg = s" Result of shutdown command is : $response")
        commandResponseManager.addOrUpdateCommand(msg.controlCommand.runId, response)
      }
      case None => {
        log.error(msg = s" Error in finding HCD instance ")
      }
    }
  }
  def handleStartupCommand(msg: submitCommandMsg): Unit = {
    log.info(msg = s"In assembly command Handler Actor submitting startup command")
    val setup = Setup(mcsHCDPrefix, CommandName(Commands.STARTUP), msg.controlCommand.maybeObsId)
    log.info(msg = s" In handleStarupCommand hcdLocation is ${hcdLocation}")
    hcdLocation match {
      case Some(commandService: CommandService) => {
        val response = Await.result(commandService.submit(setup), 5.seconds)
        //log.info(msg = s" Result of startup command is : $response")
        commandResponseManager.addSubCommand(msg.controlCommand.runId, response.runId)
        commandResponseManager.updateSubCommand(response.runId, response)
        log.info(msg = s"Successfully updated status of startup command in commandResponseManager : ${response}")
      }
      case None => {
        log.error(msg = s" Error in finding HCD instance while submitting startup command to HCD ")
      }
    }
  }

  def handleDatumCommand(msg: submitCommandMsg) = {
    log.info(msg = "Sending  Datum command to DatumCommandActor")

    val datumCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(DatumCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "DatumCommandActor")
    datumCommandActor ! msg.controlCommand
  }

  def handleMoveCommand(msg: submitCommandMsg) = {
    //log.info(msg = "Sending  Move command to MoveCommandActor")
    val moveCommandActor: ActorRef[ControlCommand] =
      ctx.spawn(MoveCommandActor.createObject(commandResponseManager, hcdLocation, loggerFactory), "MoveCommandActor")
    moveCommandActor ! msg.controlCommand
  }

  def handleFollowCommand(msg: ImmediateCommand): Unit = {
    // log.info(msg = "Sending  Follow command to FollowCommandActor")
    val followCommandActor: ActorRef[ImmediateCommand] =
      ctx.spawn(FollowCommandActor.createObject(hcdLocation, loggerFactory), "FollowCommandActor")
    followCommandActor ! msg
  }

}
