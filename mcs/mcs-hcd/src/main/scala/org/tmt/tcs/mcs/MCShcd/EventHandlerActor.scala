package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.framework.scaladsl.CurrentStatePublisher
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.models.{Prefix, Subsystem}
import csw.messages.params.states.CurrentState
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage._

sealed trait EventMessage
object EventMessage {
  case class HCDLifeCycleStateChangeMsg(lifeCycleState: HCDLifeCycleState.lifeCycleState)         extends EventMessage
  case class HCDOperationalStateChangeMsg(operationalState: HCDOperationalState.operationalState) extends EventMessage
  case class GetCurrentState(sender: ActorRef[EventMessage])                                      extends EventMessage
  case class HcdCurrentState(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                             operationalState: HCDOperationalState.operationalState)
      extends EventMessage
}

object HCDLifeCycleState extends Enumeration {
  type lifeCycleState = Value
  val Off, Ready, Loaded, Initialized, Running, Degraded, Disconnected, Faulted = Value
}
object HCDOperationalState extends Enumeration {
  type operationalState = Value
  val DrivePowerOff, ServoOffDrivePowerOn, ServoOffDatumed, PointingDrivePowerOn, PointingDatumed, Following, StowingDrivePowerOn,
  StowingDatumed = Value
}
object EventHandlerActor {
  def createObject(currentStatePublisher: CurrentStatePublisher,
                   lifeCycleState: HCDLifeCycleState.lifeCycleState,
                   operationalState: HCDOperationalState.operationalState,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.mutable(ctx => EventHandlerActor(ctx, currentStatePublisher, lifeCycleState, operationalState, loggerFactory))

}
case class EventHandlerActor(ctx: ActorContext[EventMessage],
                             currentStatePublisher: CurrentStatePublisher,
                             lifeCycleState: HCDLifeCycleState.lifeCycleState,
                             operationalState: HCDOperationalState.operationalState,
                             loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[EventMessage] {
  private val log                 = loggerFactory.getLogger
  private val prefix              = Prefix(Subsystem.MCS, "tmt.tcs.mcs.hcd")
  private val lifecycleStateKey   = KeyType.StringKey.make("hcdLifeCycleState")
  private val operationalStateKey = KeyType.StringKey.make(name = "hcdOperationalState")

  def onLifeCycleStateChange(msg: HCDLifeCycleStateChangeMsg) = {
    log.info(msg = s"Handling $msg in hcd")
    val currLifeCycleState = msg.lifeCycleState
    val state              = currLifeCycleState.toString
    log.info(msg = s"Changed lifecycle state of MCS HCD is : ${state}")
    val lifeCycleParam: Parameter[String] = lifecycleStateKey.set(state)

    val currentState = CurrentState(prefix).add(lifeCycleParam)
    EventHandlerActor.createObject(currentStatePublisher, currLifeCycleState, operationalState, loggerFactory)
    currentStatePublisher.publish(currentState)
    log.info(msg = s"published state : ${currentState} to MCS Assembly")
  }

  def onOperationalStateChange(msg: HCDOperationalStateChangeMsg) = {
    log.info(msg = s"Handling $msg in hcd")
    val currOperationalState = msg.operationalState
    val state                = currOperationalState.toString
    log.info(msg = s"Changed operational state of MCS HCD is : ${state}")
    val operationalStateParam: Parameter[String] = operationalStateKey.set(state)

    val currentState = CurrentState(prefix).add(operationalStateParam)
    EventHandlerActor.createObject(currentStatePublisher, lifeCycleState, currOperationalState, loggerFactory)
    currentStatePublisher.publish(currentState)
    log.info(msg = s"published state : ${currentState} to MCS Assembly")
  }

  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    log.info(msg = s"Received event : $msg ")
    msg match {
      case msg: HCDLifeCycleStateChangeMsg   => onLifeCycleStateChange(msg)
      case msg: HCDOperationalStateChangeMsg => onOperationalStateChange(msg)
      case msg: GetCurrentState => {
        log.info(
          msg =
            s"Sending current lifecyclestate :  ${lifeCycleState} and operationalState : ${operationalState} to sender : ${msg.sender}"
        )
        msg.sender ! HcdCurrentState(lifeCycleState, operationalState)
      }
      case _ => {
        log.error(msg = s"Unknown $msg is sent to EventHandlerActor")
        Behavior.unhandled
      }
    }
    this
  }
}
