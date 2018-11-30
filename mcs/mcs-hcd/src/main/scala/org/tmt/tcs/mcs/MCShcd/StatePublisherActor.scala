package org.tmt.tcs.mcs.MCShcd

import java.time
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
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.{ProcCurrStateDemand, ProcEventDemand}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, SimpleSimulator, ZeroMQMessage}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.{PublishCurrStateToZeroMQ, PublishEvent, StartSimulEventSubscr}
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{MCSPositionDemand, ParamSetTransformer}

import scala.concurrent.ExecutionContext.Implicits.global
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
  case class StartEventSubscription(zeroMQProtoActor: ActorRef[ZeroMQMessage], simpleSimActor: ActorRef[SimpleSimMsg])
      extends EventMessage
  case class PublishState(currentState: CurrentState) extends EventMessage
  case class AssemblyStateChange(zeroMQProtoActor: ActorRef[ZeroMQMessage],
                                 simpleSimActor: ActorRef[SimpleSimMsg],
                                 currentState: CurrentState)
      extends EventMessage
  case class SimulationModeChange(simMode: String, simpleSimActor: ActorRef[SimpleSimMsg], zeroMQActor: ActorRef[ZeroMQMessage])
      extends EventMessage
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
                   simulatorMode: String,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        StatePublisherActor(ctx,
                            currentStatePublisher,
                            lifeCycleState,
                            operationalState,
                            eventService,
                            simulatorMode,
                            loggerFactory)
    )

}

/*
This actor is responsible for publishing state, events for MCS to assembly it dervies HCD states from
MCS state and  events received
 */
case class StatePublisherActor(ctx: ActorContext[EventMessage],
                               currentStatePublisher: CurrentStatePublisher,
                               lifeCycleState: HCDLifeCycleState.lifeCycleState,
                               operationalState: HCDOperationalState.operationalState,
                               eventService: EventService,
                               simulatorMode: String,
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[EventMessage] {
  private val log                                      = loggerFactory.getLogger
  private val prefix                                   = Prefix(Subsystem.MCS.toString)
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  private var zeroMQActor: ActorRef[ZeroMQMessage]     = null
  private var simpleSimActor: ActorRef[SimpleSimMsg]   = null

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  /*
    This function is used in case of EventPublisher for publishing demands to MCS Simulator
   */
  private def processEvent(event: Event): Future[_] = {
    //log.info(msg = s"*** Received positionDemands: ${event} to HCD StatePublisherActor at ${System.currentTimeMillis()} ***")
    event match {
      case systemEvent: SystemEvent => {
        // val mcsDemandPositions: MCSPositionDemand = paramSetTransformer.getMountDemandPositions(systemEvent)
        val sysEvent = systemEvent.add(EventConstants.HcdReceivalTime_Key.set(System.currentTimeMillis()))
        if (Commands.REAL_SIMULATOR == simulatorMode) {
          //  log.error(s"Sending event : ${sysEvent} to RealSimulator")
          zeroMQActor ! PublishEvent(sysEvent)
        } else {
          //log.error(s"Sending event : ${sysEvent} to SimpleSimulator")
          simpleSimActor ! ProcEventDemand(sysEvent)
        }
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
    msg match {
      case msg: StartEventSubscription => {
        val eventSubscriber = eventService.defaultSubscriber
        zeroMQActor = msg.zeroMQProtoActor
        log.info(msg = s"Starting subscribing to events from MCS Assembly in StatePublisherActor via EventSubscriber")
        eventSubscriber.subscribeCallback(EventConstants.PositionDemandKey, event => processEvent(event))
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
                                         simulatorMode,
                                         loggerFactory)
      }
      case msg: PublishState => {
        if (simulatorMode == Commands.SIMPLE_SIMULATOR) {
          val hcdReceivalTime: Parameter[Long] = EventConstants.hcdEventReceivalTime_Key.set(System.currentTimeMillis())
          val currentState                     = msg.currentState.add(hcdReceivalTime)
          currentStatePublisher.publish(currentState)
          Behavior.same

        } else {
          currentStatePublisher.publish(msg.currentState)
          Behavior.same
        }

      }
      case msg: HCDOperationalStateChangeMsg => {
        val currOperationalState = msg.operationalState
        log.info(msg = s"Changing current operational state of MCS HCD to: ${currOperationalState}")
        StatePublisherActor.createObject(currentStatePublisher,
                                         lifeCycleState,
                                         currOperationalState,
                                         eventService,
                                         simulatorMode,
                                         loggerFactory)
      }
      case msg: SimulationModeChange => {
        log.info(s"Changing Simulation mode in StatePublisherActor from : $simulatorMode to ${msg.simMode}")
        if (msg.simMode == Commands.SIMPLE_SIMULATOR) {
          simpleSimActor = msg.simpleSimActor
          //simpleSimActor ! StartPublishingEvent()
          log.info(s"Started Publishing Events from MCS SimpleSimulator")
        } else {
          log.info(s"Started Publishing events from MCS ZeroMQActor")
          zeroMQActor = msg.zeroMQActor
          zeroMQActor ! StartSimulEventSubscr()
        }
        StatePublisherActor.createObject(currentStatePublisher,
                                         lifeCycleState,
                                         operationalState,
                                         eventService,
                                         msg.simMode,
                                         loggerFactory)
      }
      case msg: AssemblyStateChange => {
        //TODO: Below HCD Event Receival time is temporary it must be removed once performance measurement is done.
        //log.info(s"Received assembly state change : ${msg}")
        try {
          val currentState = msg.currentState
          val currState    = currentState.add(EventConstants.HcdReceivalTime_Key.set(System.currentTimeMillis()))
          if (simulatorMode == Commands.REAL_SIMULATOR) {
            // log.info(s"Sending demands to Real Simulator")
            zeroMQActor = msg.zeroMQProtoActor
            zeroMQActor ! PublishCurrStateToZeroMQ(currState)
            //log.info(s"Published ${currState} to zeroMQActor")
          } else {
            //log.info(s"Sending demands to Simple Simulator")
            simpleSimActor = msg.simpleSimActor
            simpleSimActor ! ProcCurrStateDemand(currState)
            //log.info(s"Published ${currState} to simpleSimulator")
          }

        } catch {
          case ex: Exception => {
            ex.printStackTrace()
            log.error("Exception in getting current state in HCD")
          }
        }
        Behavior.same
      }
      //TODO : In case later decesion changed to use this message then rewrite publishCurrentPosition case
      /*case msg: StartPublishing => {
        timer.startPeriodicTimer(TimerKey, publishCurrentPosition(), Duration.create(10, TimeUnit.SECONDS))
        Behavior.same
      }*/

      case msg: GetCurrentState => {
        /* log.info(
          msg =
            s"Sending current lifecyclestate :  ${lifeCycleState} and operationalState : ${operationalState} to sender : ${msg.sender}"
        )*/
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
