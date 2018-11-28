package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.command.CommandResponseManager
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.ImmediateCommandResponse
import org.tmt.tcs.mcs.MCShcd.constants.Commands

import scala.concurrent.Await

object StartupCmdActor {
  def create(commandResponseManager: CommandResponseManager,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => StartupCmdActor(ctx, commandResponseManager, zeroMQProtoActor, simpleSimActor, simulatorMode, loggerFactory)
    )
}
case class StartupCmdActor(ctx: ActorContext[ControlCommand],
                           commandResponseManager: CommandResponseManager,
                           zeroMQProtoActor: ActorRef[ZeroMQMessage],
                           simpleSimActor: ActorRef[SimpleSimMsg],
                           simulatorMode: String,
                           loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log: Logger = loggerFactory.getLogger

  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    //  log.info(s"Submitting startup  command with id : ${msg.runId} to simulator")

    simulatorMode match {
      case Commands.REAL_SIMULATOR => {
        submitToRealSim(msg)
        Behaviors.stopped
      }
      case Commands.SIMPLE_SIMULATOR => {
        submitToSimpleSim(msg)
        Behaviors.stopped
      }
    }

    Behavior.stopped
  }
  private def submitToSimpleSim(msg: ControlCommand): Unit = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: SimpleSimMsg = Await.result(simpleSimActor ? { ref: ActorRef[SimpleSimMsg] =>
      SimpleSimMsg.ProcessCommand(msg, ref)
    }, 10.seconds)
    response match {
      case x: SimpleSimMsg.SimpleSimResp => {
        commandResponseManager.addOrUpdateCommand(msg.runId, x.commandResponse)
        //msg ! ImmediateCommandResponse()
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(msg.runId,
                                                  CommandResponse.Error(msg.runId, "Unable to submit command to SimpleSimulator"))
      }
    }
  }
  private def submitToRealSim(msg: ControlCommand): Unit = {
    implicit val duration: Timeout = 10 seconds
    implicit val scheduler         = ctx.system.scheduler
    val response: ZeroMQMessage = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
      ZeroMQMessage.SubmitCommand(ref, msg)
    }, 10.seconds)
    response match {
      case x: ZeroMQMessage.MCSResponse => {
        // log.info(s"Response from MCS for command runID : ${msg.runId} is : ${x}")
        commandResponseManager.addOrUpdateCommand(msg.runId, x.commandResponse)
      }
      case _ => {
        commandResponseManager.addOrUpdateCommand(
          msg.runId,
          CommandResponse.Error(msg.runId, "Unable to submit command data to MCS subsystem from worker actor.")
        )
      }
    }
  }
}
