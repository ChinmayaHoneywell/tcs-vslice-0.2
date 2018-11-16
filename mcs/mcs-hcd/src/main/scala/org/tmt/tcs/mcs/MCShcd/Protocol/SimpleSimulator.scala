package org.tmt.tcs.mcs.MCShcd.Protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.events.SystemEvent
import csw.messages.params.states.CurrentState
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg._

sealed trait SimpleSimMsg
object SimpleSimMsg {
  case class ProcessCommand(command: ControlCommand, sender: ActorRef[SimpleSimMsg]) extends SimpleSimMsg
  case class StartPublishingEvent()                                                  extends SimpleSimMsg
  case class SimpleSimResp(commandResponse: CommandResponse)                         extends SimpleSimMsg
  case class ProcEventDemand(event: SystemEvent)                                     extends SimpleSimMsg
  case class ProcOneWayDemand(command: ControlCommand)                               extends SimpleSimMsg
  case class ProcCurrStateDemand(currState: CurrentState)                            extends SimpleSimMsg
}

object SimpleSimulator {
  def create(loggerFactory: LoggerFactory): Behavior[SimpleSimMsg] =
    Behaviors.setup(ctx => SimpleSimulator(ctx, loggerFactory))
}
case class SimpleSimulator(ctx: ActorContext[SimpleSimMsg], loggerFactory: LoggerFactory) extends MutableBehavior[SimpleSimMsg] {
  private val log: Logger = loggerFactory.getLogger
  override def onMessage(msg: SimpleSimMsg): Behavior[SimpleSimMsg] = {
    msg match {
      case msg: ProcessCommand => {
        log.info(s"Received command : ${msg.command} in simpleSimulator.")
        msg.sender ! SimpleSimResp(CommandResponse.Completed(msg.command.runId))
        Behavior.same
      }
      case msg: ProcOneWayDemand => {
        log.info(s"${msg.command},${System.currentTimeMillis()}")
        Behavior.same
      }
      case msg: ProcEventDemand => {
        log.info(s"Received demands : ${msg.event}")
        Behavior.same
      }
      case msg: ProcCurrStateDemand => {
        log.info(s"${msg.currState},${System.currentTimeMillis()}")
        Behavior.same
      }
      case msg: StartPublishingEvent => {
        Behavior.same
      }
    }
  }
}
