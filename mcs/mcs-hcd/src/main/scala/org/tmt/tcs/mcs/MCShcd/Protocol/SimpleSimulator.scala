package org.tmt.tcs.mcs.MCShcd.Protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.events.SystemEvent
import csw.messages.params.generics.Parameter
import csw.messages.params.models.Units.degree
import csw.messages.params.models.{Prefix, Subsystem}
import csw.messages.params.states.{CurrentState, StateName}
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
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
  def create(loggerFactory: LoggerFactory, statePublisherActor: ActorRef[EventMessage]): Behavior[SimpleSimMsg] =
    Behaviors.setup(ctx => SimpleSimulator(ctx, loggerFactory, statePublisherActor))
}
case class SimpleSimulator(ctx: ActorContext[SimpleSimMsg],
                           loggerFactory: LoggerFactory,
                           statePublisherActor: ActorRef[EventMessage])
    extends MutableBehavior[SimpleSimMsg] {
  private val log: Logger   = loggerFactory.getLogger
  var az: Double            = 0.0
  var el: Double            = 0.0
  val prefix: Prefix        = Prefix(Subsystem.MCS.toString)
  val MIN_AZ_POS: Double    = -330
  val MAX_AZ_POS: Double    = 170
  val MIN_EL_POS: Double    = -3
  val MAX_EL_POS: Double    = 93
  var AzPosDemanded: Double = 15
  var ElPosDemanded: Double = 15

  override def onMessage(msg: SimpleSimMsg): Behavior[SimpleSimMsg] = {
    msg match {
      case msg: ProcessCommand => {
        //log.info(s"Received command : ${msg.command} in simpleSimulator.")
        msg.sender ! SimpleSimResp(CommandResponse.Completed(msg.command.runId))
        Behavior.same
      }
      case msg: ProcOneWayDemand => {
        //log.info(s"Received position demands from MCSH :")
        val simulatorRecTime                 = System.currentTimeMillis()
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

        log.error(
          s"${azPos.head}, ${elPos.head}, ${sentTime.head}, ${assemblyRecTime.head}, ${hcdRecTime.head}, ${simulatorRecTime}"
        )
        Behavior.same
      }
      case msg: ProcEventDemand => {
        val cs               = msg.event
        val simpleSimRecTime = System.currentTimeMillis()
        val assemblyRecTime  = cs.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
        val hcdRecTime       = cs.get(EventConstants.HcdReceivalTime_Key).get.head
        val tpkPublishTime   = cs.get(EventConstants.TimeStampKey).get.head
        val azPos            = cs.get(EventConstants.AzPosKey).get.head
        val elPos            = cs.get(EventConstants.ElPosKey).get.head
        az = azPos
        el = elPos
        log.error(s"Received event :$azPos, $elPos, $tpkPublishTime, $assemblyRecTime, $hcdRecTime, $simpleSimRecTime")
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
        az = azPos
        el = elPos
        setAzPosDemanded(az)
        setElPosDemanded(el)
        log.info(s"Received currentState :$azPos, $elPos, $tpkPublishTime, $assemblyRecTime, $hcdRecTime, $simpleSimRecTime")
        Behavior.same
      }
      case msg: StartPublishingEvent => {
        log.info(s"Starting event publishing from Simple simulator")
        new Thread(new Runnable {
          override def run(): Unit = startPublishingCurrPos()
        }).start()

        new Thread(new Runnable {
          override def run(): Unit = startPublishingHealth()
        }).start()
        log.info(s"Successfully started publishing  current position and health events from Simple simulator")
        Behavior.same
      }
    }
  }
  private def setElPosDemanded(elDemanded: Double) = {
    if (elDemanded >= MAX_EL_POS) {
      ElPosDemanded = MAX_EL_POS
    } else if (elDemanded <= MIN_EL_POS) {
      ElPosDemanded = MIN_EL_POS
    } else {
      ElPosDemanded = elDemanded
    }
  }

  private def setAzPosDemanded(azDemanded: Double) = {
    if (azDemanded >= MAX_AZ_POS) {
      AzPosDemanded = MAX_AZ_POS
    } else if (azDemanded <= MIN_AZ_POS) {
      AzPosDemanded = MIN_AZ_POS
    } else {
      AzPosDemanded = azDemanded
    }
  }
  def updateCurrentElPos(): Double = {
    if (el < ElPosDemanded) {
      if (el + 5 < MAX_EL_POS) {
        el = el + 5
      } else {
        el = MAX_AZ_POS
      }
    }
    el
  }
  def updateCurrentAzPos(): Double = {
    if (az < AzPosDemanded) {
      if (az + 5 < MAX_AZ_POS) {
        az = az + 5
      } else {
        az = MAX_AZ_POS
      }
    }
    az
  }
  def startPublishingCurrPos(): Unit = {
    log.info(s"Publish Current position thread started")
    while (true) {
      Thread.sleep(10)
      //updateCurrentAzPos()
      //updateCurrentElPos()
      val currentTime = System.currentTimeMillis()

      val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(updateCurrentAzPos()).withUnits(degree)
      val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(updateCurrentElPos()).withUnits(degree)

      val azPosErrorParam: Parameter[Double] =
        EventConstants.AZ_POS_ERROR_KEY.set(az).withUnits(degree)
      val elPosErrorParam: Parameter[Double] =
        EventConstants.EL_POS_ERROR_KEY.set(el).withUnits(degree)

      val azInPositionParam: Parameter[Boolean] = EventConstants.AZ_InPosition_Key.set(true)
      val elInPositionParam: Parameter[Boolean] = EventConstants.EL_InPosition_Key.set(true)
      val timestamp                             = EventConstants.TimeStampKey.set(currentTime)
      val hcdRecvTimeKey                        = EventConstants.hcdEventReceivalTime_Key.set(currentTime)

      val currentState = CurrentState(prefix, StateName(EventConstants.CURRENT_POSITION))
        .add(azPosParam)
        .add(elPosParam)
        .add(azPosErrorParam)
        .add(elPosErrorParam)
        .add(azInPositionParam)
        .add(elInPositionParam)
        .add(timestamp)
        .add(hcdRecvTimeKey)
      statePublisherActor ! PublishState(currentState)
    }
  }

  def startPublishingHealth(): Unit = {
    //println("Publish Health Thread Started")
    while (true) {
      Thread.sleep(1000)
      val currentTime                          = System.currentTimeMillis()
      val healthParam: Parameter[String]       = EventConstants.HEALTH_KEY.set("Good")
      val healthReasonParam: Parameter[String] = EventConstants.HEALTH_REASON_KEY.set("Good Reason")
      val timestamp                            = EventConstants.TimeStampKey.set(currentTime)
      val hcdRecvTimeKey                       = EventConstants.hcdEventReceivalTime_Key.set(currentTime)
      val currentState = CurrentState(prefix, StateName(EventConstants.HEALTH_STATE))
        .add(healthParam)
        .add(healthReasonParam)
        .add(timestamp)
        .add(hcdRecvTimeKey)
      statePublisherActor ! PublishState(currentState)
    }
  }
}
