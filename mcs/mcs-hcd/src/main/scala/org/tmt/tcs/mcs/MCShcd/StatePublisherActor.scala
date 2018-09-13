package org.tmt.tcs.mcs.MCShcd

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior, TimerScheduler}
import csw.framework.CurrentStatePublisher
import csw.messages.events.{Event, SystemEvent}
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.models.Units.degree
import csw.messages.params.models.{Prefix, Subsystem}
import csw.messages.params.states.{CurrentState, StateName}
import csw.services.event.api.scaladsl.{EventService, EventSubscriber}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage._
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.PublishEvent
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{MCSPositionDemand, ParamSetTransformer}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration

sealed trait EventMessage
object EventMessage {
  case class HCDOperationalStateChangeMsg(operationalState: HCDOperationalState.operationalState) extends EventMessage

  case class StateChangeMsg(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                            oerationalState: HCDOperationalState.operationalState)
      extends EventMessage

  case class GetCurrentState(sender: ActorRef[EventMessage]) extends EventMessage
  case class HcdCurrentState(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                             operationalState: HCDOperationalState.operationalState)
      extends EventMessage
  // case class StartPublishing() extends EventMessage
  case class StartEventSubscription(zeroMQProtoActor: ActorRef[ZeroMQMessage]) extends EventMessage
  case class PublishState(currentState: CurrentState)                          extends EventMessage

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
                   eventService: EventService,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx => StatePublisherActor(ctx, currentStatePublisher, lifeCycleState, operationalState, eventService, loggerFactory)
    )

}
//private case object TimerKey
/*
This actor is responsible for publishing state, events for MCS to assembly it dervies HCD states from
MCS state and  events received
 */
case class StatePublisherActor(ctx: ActorContext[EventMessage],
                               currentStatePublisher: CurrentStatePublisher,
                               lifeCycleState: HCDLifeCycleState.lifeCycleState,
                               operationalState: HCDOperationalState.operationalState,
                               eventService: EventService,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[EventMessage] {
  private val log    = loggerFactory.getLogger
  private val prefix = Prefix(Subsystem.MCS.toString)
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  // private val protocolImpl : IProtocol = ZeroMQProtocolImpl.create(loggerFactory)
  private var zeroMQActor: ActorRef[ZeroMQMessage] = null

  /*

   */
  private def processEvent(event: Event): Future[_] = {
    event match {
      case systemEvent: SystemEvent => {
        val mcsDemandPositions: MCSPositionDemand = paramSetTransformer.getMountDemandPositions(systemEvent)
        zeroMQActor ! PublishEvent(mcsDemandPositions)
        Future.successful("Successfully sent Assembly position demands to MCS ZeroMQActor")
      }
    }
  }
  /*
       This function performs following tasks:
       - On receipt of StartEventSubscription message this function starts subscribing to positionDemand event
        from MCS Assembly using CSW EventService's default EventSubscriber.
       - On receipt of StateChangeMsg message this function publishes HCD's Lifecycle state to Assembly using
          CSW CurrentStatePublisher
       - On receipt of HCDOperationalStateChangeMsg it simply changes Actor's behavior to changed operational state
       and does not publish anything
       - On receipt of GetCurrentState msg it returns current lifecycle and operational state of HCD to caller

   */
  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    log.info(msg = s"Received event : $msg ")
    msg match {
      case msg: StartEventSubscription => {
        val eventSubscriber: Future[EventSubscriber] = eventService.defaultSubscriber
        zeroMQActor = msg.zeroMQProtoActor
        log.info(msg = s"Starting subscribing to events from MCS Assembly in StatePublisherActor via EventSubscriber")
        eventSubscriber.onComplete() {
          case subscriber: EventSubscriber => {
            subscriber.subscribeAsync(EventConstants.PositionDemandKey, event => processEvent(event))
          }
          case _ => {
            log.error("Unable to get subscriber instance from EventService.")
            Future.failed(new Exception("Unable to get event subscriber instance from EventService "))
          }
        }
        Behavior.same
      }
      case msg: StateChangeMsg => {
        val currLifeCycleState = msg.lifeCycleState
        val state              = currLifeCycleState.toString
        log.info(msg = s"Changed lifecycle state of MCS HCD is : ${state} and publishing the same to the MCS Assembly")
        val currentState: CurrentState = paramSetTransformer.getHCDState(state)
        currentStatePublisher.publish(currentState)
        log.info(msg = s"Successfully published state :${currentState} to ASSEMBLY")
        StatePublisherActor.createObject(currentStatePublisher,
                                         currLifeCycleState,
                                         msg.oerationalState,
                                         eventService,
                                         loggerFactory)
      }
      case msg: PublishState => {
        currentStatePublisher.publish(msg.currentState)
        Behavior.same
      }
      case msg: HCDOperationalStateChangeMsg => {
        val currOperationalState = msg.operationalState
        log.info(msg = s"Changing current operational state of MCS HCD to: ${currOperationalState}")
        StatePublisherActor.createObject(currentStatePublisher, lifeCycleState, currOperationalState, eventService, loggerFactory)
      }
      //TODO : In case later decesion changed to use this message then rewrite publishCurrentPosition case
      /*case msg: StartPublishing => {
        timer.startPeriodicTimer(TimerKey, publishCurrentPosition(), Duration.create(10, TimeUnit.SECONDS))
        Behavior.same
      }*/

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
