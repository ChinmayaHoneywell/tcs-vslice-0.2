package org.tmt.tcs.mcs.MCSassembly

import java.util.{Calendar, Date}

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.ControlCommand
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import org.tmt.tcs.mcs.MCSassembly.CommandMessage.{ImmediateCommand, ImmediateCommandResponse}

object FollowCommandActor {
  def createObject(hcdLocation: Option[CommandService], loggerFactory: LoggerFactory): Behavior[ImmediateCommand] =
    Behaviors.setup(ctx => FollowCommandActor(ctx, hcdLocation, loggerFactory))
}
/*
This actor is responsible for processing of Follow command. It sends follow command to
hcd and sends the response from HCD to the caller in ImmediateCommand message
 */
case class FollowCommandActor(ctx: ActorContext[ImmediateCommand],
                              hcdLocation: Option[CommandService],
                              loggerFactory: LoggerFactory)
    extends MutableBehavior[ImmediateCommand] {
  private val log                = loggerFactory.getLogger
  implicit val duration: Timeout = 20 seconds
  /*
      This method sends follow command to HCD if commandService instance is available and sends HCD response
      to the caller else if command service instance is not available then
      it will send error message to caller
   */
  override def onMessage(command: ImmediateCommand): Behavior[ImmediateCommand] = {
    val startTime: Long = Calendar.getInstance().getTimeInMillis
    log.info(
      msg =
        s"Starting execution of Follow command id : ${command.controlCommand.runId} in Assembly worker actor  currentTime : ${startTime}"
    )
    var endTime: Long = startTime
    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submit(command.controlCommand), 3.seconds)
        log.info(msg = s" updating follow command : ${command.controlCommand.runId} with response : ${response} ")
        command.sender ! ImmediateCommandResponse(response)
        //commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        endTime = Calendar.getInstance().getTimeInMillis
        val diff: Long = endTime - startTime
        log.info(
          msg = s"completed follow command in assembly worker actor id : ${command.controlCommand.runId} time taken is :  ${diff}"
        )
        Behavior.stopped
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        endTime = Calendar.getInstance().getTimeInMillis
        val diff: Long = endTime - startTime
        log.info(
          msg = s"completed follow command in assembly worker actor id : ${command.controlCommand.runId} time taken is :  ${diff}"
        )
        Behavior.unhandled
      }
    }
  }
}
