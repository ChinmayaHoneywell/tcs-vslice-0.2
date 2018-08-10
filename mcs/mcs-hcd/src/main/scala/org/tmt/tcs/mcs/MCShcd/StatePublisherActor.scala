package org.tmt.tcs.mcs.MCShcd

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, MutableBehavior, TimerScheduler}
import csw.framework.scaladsl.CurrentStatePublisher
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.models.Units.degree
import csw.messages.params.models.{Prefix, Subsystem}
import csw.messages.params.states.{CurrentState, StateName}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage._
import org.tmt.tcs.mcs.MCShcd.simulator.CurrentPosition

import scala.concurrent.duration.Duration

sealed trait EventMessage
object EventMessage {
  case class publishCurrentPosition()                                                             extends EventMessage
  case class HCDOperationalStateChangeMsg(operationalState: HCDOperationalState.operationalState) extends EventMessage

  case class StateChangeMsg(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                            oerationalState: HCDOperationalState.operationalState)
      extends EventMessage

  case class GetCurrentState(sender: ActorRef[EventMessage]) extends EventMessage

  case class HcdCurrentState(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                             operationalState: HCDOperationalState.operationalState)
      extends EventMessage
  case class StartPublishing() extends EventMessage

}

object HCDLifeCycleState extends Enumeration {
  type lifeCycleState = Value
  val Off, Ready, Loaded, Initialized, Running = Value
}
object HCDOperationalState extends Enumeration {
  type operationalState = Value
  val DrivePowerOff, ServoOffDrivePowerOn, ServoOffDatumed, PointingDrivePowerOn, PointingDatumed, Following, StowingDrivePowerOn,
  StowingDatumed, Degraded, Disconnected, Faulted = Value
}
object StatePublisherActor {
  def createObject(currentStatePublisher: CurrentStatePublisher,
                   lifeCycleState: HCDLifeCycleState.lifeCycleState,
                   operationalState: HCDOperationalState.operationalState,
                   subsystemManager: SubsystemManager,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.withTimers(
      timers =>
        StatePublisherActor(timers, currentStatePublisher, lifeCycleState, operationalState, subsystemManager, loggerFactory)
    )

}
private case object TimerKey
/*
This actor is responsible for publishing state, events for MCS to assembly it dervies HCD states from
MCS state and  events received
 */
case class StatePublisherActor(timer: TimerScheduler[EventMessage],
                               currentStatePublisher: CurrentStatePublisher,
                               lifeCycleState: HCDLifeCycleState.lifeCycleState,
                               operationalState: HCDOperationalState.operationalState,
                               subsystemManager: SubsystemManager,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[EventMessage] {
  private val log               = loggerFactory.getLogger
  private val prefix            = Prefix(Subsystem.MCS.toString)
  private val lifecycleStateKey = KeyType.StringKey.make("hcdLifeCycleState")

  private val timeStampKey = KeyType.TimestampKey.make(name = "timeStamp")
  /*
         This functions updates operational state of MCS HCD
   */
  def onOperationalStateChange(msg: HCDOperationalStateChangeMsg): Behavior[EventMessage] = {
    log.info(msg = s"Handling ${msg} in hcd")
    val currOperationalState = msg.operationalState

    log.info(msg = s"current operational state of MCS HCD is: ${currOperationalState} ")
    StatePublisherActor.createObject(currentStatePublisher, lifeCycleState, currOperationalState, subsystemManager, loggerFactory)
  }
  /*
       This functions publishes HCD current lifecycle state, current position to assembly by using statePublisher actor
   */
  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    log.info(msg = s"Received event : $msg ")
    msg match {

      case msg: StateChangeMsg => {
        val currLifeCycleState = msg.lifeCycleState
        val state              = currLifeCycleState.toString
        log.info(msg = s"Changed lifecycle state of MCS HCD is : ${state} and publishing the same to the MCS Assembly")
        val lifeCycleParam: Parameter[String] = lifecycleStateKey.set(state)
        val timestamp                         = timeStampKey.set(Instant.now)

        val currentState = CurrentState(prefix, StateName("lifecycleState")).add(lifeCycleParam).add(timestamp)
        log.info(s"Publishing state : ${currentState} to assembly with currentStatePublishier : ${currentStatePublisher}")
        currentStatePublisher.publish(currentState)
        log.info(msg = s"Successfully published state to ASSEMBLY")
        StatePublisherActor.createObject(currentStatePublisher,
                                         currLifeCycleState,
                                         msg.oerationalState,
                                         subsystemManager,
                                         loggerFactory)
      }

      case msg: HCDOperationalStateChangeMsg => onOperationalStateChange(msg)
      case msg: StartPublishing => {
        timer.startPeriodicTimer(TimerKey, publishCurrentPosition(), Duration.create(10, TimeUnit.SECONDS))
        Behavior.same
      }
      case msg: publishCurrentPosition => {
        val azPosKey                         = KeyType.DoubleKey.make("azPosKey")
        val azPosErrorKey                    = KeyType.DoubleKey.make("azPosErrorKey")
        val elPosKey                         = KeyType.DoubleKey.make("elPosKey")
        val elPosErrorKey                    = KeyType.DoubleKey.make("elPosErrorKey")
        val azInPositionKey                  = KeyType.BooleanKey.make("azInPositionKey")
        val elInPositionKey                  = KeyType.BooleanKey.make("elInPositionKey")
        val currentPosition: CurrentPosition = subsystemManager.receiveCurrentPosition()

        val azPosParam        = azPosKey.set(currentPosition.azPos).withUnits(degree)
        val azPosErrorParam   = azPosErrorKey.set(currentPosition.asPosError).withUnits(degree)
        val elPosParam        = elPosKey.set(currentPosition.elPos).withUnits(degree)
        val elPosErrorParam   = elPosErrorKey.set(currentPosition.elPosError).withUnits(degree)
        val azInPositionParam = azInPositionKey.set(false)
        val elInPositionParam = elInPositionKey.set(true)

        val timestamp = timeStampKey.set(Instant.now)

        val currentState = CurrentState(prefix, StateName("currentPosition"))
          .add(azPosParam)
          .add(elPosParam)
          .add(azPosErrorParam)
          .add(elPosErrorParam)
          .add(azInPositionParam)
          .add(elInPositionParam)
          .add(timestamp)
        log.info(s"publishing current position ${currentState} to assembly")
        currentStatePublisher.publish(currentState)
        Behavior.same
      }
      case msg: GetCurrentState => {
        log.info(
          msg =
            s"Sending current lifecyclestate :  ${lifeCycleState} and operationalState : ${operationalState} to sender : ${msg.sender}"
        )
        msg.sender ! HcdCurrentState(lifeCycleState, operationalState)
        Behavior.same
      }
      case _ => {
        log.error(msg = s"Unknown ${msg} is sent to EventHandlerActor")
        Behavior.unhandled
      }
    }
  }
}
