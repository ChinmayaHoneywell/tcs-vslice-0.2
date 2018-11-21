package org.tmt.tcs.mcs.MCShcd.Protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.events.SystemEvent
import csw.messages.params.generics.Parameter
import csw.messages.params.states.CurrentState
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg._
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants

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
        //log.info(s"Received position demands from MCSH :")
        val paramSet                         = msg.command.paramSet
        val azPosParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS)
        val elPosParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS)
        //  val trackIDParam: Option[Parameter[_]]  = paramSet.find(msg => msg.keyName == EventConstants.POITNTING_KERNEL_TRACK_ID)
        val sentTimeParam: Option[Parameter[_]] = paramSet.find(msg => msg.keyName == EventConstants.TIMESTAMP)

        //val trackID  = trackIDParam.getOrElse(EventConstants.TrackIDKey.set(0))
        val azPos           = azPosParam.getOrElse(EventConstants.AzPosKey.set(0.0))
        val elPos           = elPosParam.getOrElse(EventConstants.ElPosKey.set(0.0))
        val sentTime        = sentTimeParam.getOrElse(EventConstants.TimeStampKey.set(System.currentTimeMillis()))
        val assemblyRecTime = paramSet.find(msg => msg.keyName == EventConstants.ASSEMBLY_RECEIVAL_TIME).get
        val hcdRecTime      = paramSet.find(msg => msg.keyName == EventConstants.HCD_ReceivalTime).get
        log.info(s"${azPos.head}, ${elPos.head}, ${sentTime.head}, ${assemblyRecTime.head}, ${hcdRecTime.head}")
        Behavior.same
      }
      case msg: ProcEventDemand => {
        log.info(s"Received demands : ${msg.event}")
        Behavior.same
      }
      case msg: ProcCurrStateDemand => {
        val cs               = msg.currState
        val simpleSimRecTime = System.currentTimeMillis()
        val assemblyRecTime  = cs.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = cs.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = cs.get(EventConstants.TimeStampKey).get.head
        val azPos            = cs.get(EventConstants.AzPosKey).get.head
        val elPos            = cs.get(EventConstants.ElPosKey).get.head
        log.info(s"Received currentState :$azPos, $elPos, $tpkPublishTime, $assemblyRecTime, $hcdRecTime, $simpleSimRecTime")
        Behavior.same
      }
      case msg: StartPublishingEvent => {
        Behavior.same
      }
    }
  }
}
