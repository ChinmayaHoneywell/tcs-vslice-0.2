package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.states.CurrentState
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage._
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.HCDState_Off
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.HCDState_Initialized
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.HCDState_Running
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.HCDLifecycleState
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.CURRENT_POSITION
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.DIAGNOSIS_STATE
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.HEALTH_STATE
import org.tmt.tcs.mcs.MCSassembly.Constants.EventConstants.DRIVE_STATE
import org.tmt.tcs.mcs.MCSassembly.EventMessage.PublishEvent
import org.tmt.tcs.mcs.MCSassembly.msgTransformer.EventTransformerHelper
sealed trait MonitorMessage

object AssemblyLifeCycleState extends Enumeration {
  type AssemblyState = Value
  val Initalized, Running, RunnuingOnline, RunningOffline, Shutdown = Value
}
object AssemblyOperationalState extends Enumeration {
  type AssemblyMotionState = Value
  val Ready, Running, Slewing, Halted, Tracking, Inposition, Degraded, Disconnected, Faulted = Value

}
object MonitorMessage {
  case class AssemblyLifeCycleStateChangeMsg(assemblyState: AssemblyLifeCycleState.AssemblyState) extends MonitorMessage
  case class AssemblyOperationalStateChangeMsg(assemblyMotionState: AssemblyOperationalState.AssemblyMotionState)
      extends MonitorMessage
  case class LocationEventMsg(hcdLocation: Option[CommandService]) extends MonitorMessage
  case class currentStateChangeMsg(currentState: CurrentState)     extends MonitorMessage
  case class GetCurrentState(actorRef: ActorRef[MonitorMessage])   extends MonitorMessage
  case class AssemblyCurrentState(lifeCycleState: AssemblyLifeCycleState.AssemblyState,
                                  operationalState: AssemblyOperationalState.AssemblyMotionState)
      extends MonitorMessage
  case class DiagnosisState() extends MonitorMessage
  case class HealthState()    extends MonitorMessage

}
object MonitorActor {
  def createObject(assemblyState: AssemblyLifeCycleState.AssemblyState,
                   assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
                   eventHandlerActor: ActorRef[EventMessage],
                   loggerFactory: LoggerFactory): Behavior[MonitorMessage] =
    Behaviors.setup(ctx => MonitorActor(ctx, assemblyState, assemblyMotionState, eventHandlerActor, loggerFactory))

}
/*
This actor is responsible for maintaing state of MCS assembly
 */
case class MonitorActor(ctx: ActorContext[MonitorMessage],
                        assemblyState: AssemblyLifeCycleState.AssemblyState,
                        assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
                        eventHandlerActor: ActorRef[EventMessage],
                        loggerFactory: LoggerFactory)
    extends MutableBehavior[MonitorMessage] {

  private val log                                      = loggerFactory.getLogger
  private val eventTransformer: EventTransformerHelper = EventTransformerHelper.create(loggerFactory)
  /*
  This function updates states as per messages received and publishes current states as per
  request recevied
   */
  override def onMessage(msg: MonitorMessage): Behavior[MonitorMessage] = {
    msg match {
      case x: AssemblyLifeCycleStateChangeMsg   => onAssemblyLifeCycleStateChangeMsg(x)
      case x: AssemblyOperationalStateChangeMsg => onAssemblyOperationalStateChangeMsg(x)
      case x: LocationEventMsg                  => onLocationEvent(x.hcdLocation)
      case x: currentStateChangeMsg             => onCurrentStateChange(x)
      case x: GetCurrentState => {
        log.info(s"Current lifeCycle state of assembly is : ${assemblyState} and operational state is : ${assemblyMotionState}")
        x.actorRef ! AssemblyCurrentState(assemblyState, assemblyMotionState)
        Behavior.same
      }
      case _ => {
        log.error(msg = s"Incorrect message $msg is sent to MonitorActor")
        Behavior.unhandled
      }
    }
    // this
  }
  /*
  This function updates assembly lifecycle state
   */
  def onAssemblyLifeCycleStateChangeMsg(x: MonitorMessage with AssemblyLifeCycleStateChangeMsg): Behavior[MonitorMessage] = {
    log.info(msg = s"Successfully changed monitor assembly lifecycle state to ${x.assemblyState}")
    MonitorActor.createObject(x.assemblyState, assemblyMotionState, eventHandlerActor, loggerFactory)
  }
  /*
 This function updates assembly operational state
   */
  def onAssemblyOperationalStateChangeMsg(x: MonitorMessage with AssemblyOperationalStateChangeMsg): Behavior[MonitorMessage] = {
    log.info(msg = s"Successfully changed monitor actor state to ${x.assemblyMotionState}")
    MonitorActor.createObject(assemblyState, x.assemblyMotionState, eventHandlerActor, loggerFactory)
  }
  /*
  This function receives hcd lifecycle state, current position and other current states
   amd accordingly derives assembly operational state and publishes HCD current states to eventHandler Actor
   for publishing to other TCS Assemblies
   */
  def onCurrentStateChange(x: MonitorMessage with currentStateChangeMsg): Behavior[MonitorMessage] = {

    val currentState: CurrentState = x.currentState
    log.info(msg = s"Received state change msg from HCD")
    currentState.stateName.name match {
      case HCDLifecycleState => {
        log.info("Received life cycle state change message from HCD updating state of assembly corresponding to change")
        updateAssemblyState(currentState)
      }
      case CURRENT_POSITION => {
        processMCSCurrentPositionEvent(currentState)
        eventHandlerActor ! PublishEvent(eventTransformer.getCurrentPositionEvent(currentState))

        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, loggerFactory)
      }
      case DIAGNOSIS_STATE => {
        eventHandlerActor ! PublishEvent(eventTransformer.getDiagnosisEvent(currentState))
        Behavior.same
      }
      case HEALTH_STATE => {
        eventHandlerActor ! PublishEvent(eventTransformer.getHealthEvent(currentState))
        Behavior.same
      }
      case DRIVE_STATE => {
        eventHandlerActor ! PublishEvent(eventTransformer.getDriveState(currentState))
        Behavior.same
      }
    }
  }
  /*
    TODO : here should be the logic to change assembly states based on currentPosition such as slewing,tracking
   */
  private def processMCSCurrentPositionEvent(currentState: CurrentState): Unit = {}
  /*
    This function processes currentState received from HCD CurrentStatePublisher
    - if hcdLifeCycleState is Running then it updates Assembly lifecycle and operational state to running
      and converts assemblys state to CSW SystemEvent and sends the same to EventHandlerActor
    - if hcdLifeCycleState is Initialized then lifecycle and operational state of assembly doesnot change so
      message is not sent to eventHandler actor.
    - if hcd lifecycle state is off then assembly's lifecycle and operational state is updated to shutdown and
      disconnected accordingly same is sent to communicated to eventHandlerActor

   */
  private def updateAssemblyState(currentState: CurrentState): Behavior[MonitorMessage] = {

    val optHcdLifeCycleStateParam: Option[Parameter[String]] =
      currentState.get(EventConstants.HCDLifecycleState, KeyType.StringKey)
    val hcdLifeCycleState = optHcdLifeCycleStateParam.get.head
    hcdLifeCycleState match {
      case HCDState_Running => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so changing lifecycle state of Assembly to Running "
        )
        eventHandlerActor ! PublishEvent(
          eventTransformer
            .getAssemblyEvent(AssemblyCurrentState(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running))
        )
        MonitorActor.createObject(AssemblyLifeCycleState.Running,
                                  AssemblyOperationalState.Running,
                                  eventHandlerActor,
                                  loggerFactory)
      }
      case HCDState_Initialized => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so not changing lifecycle state of Assembly assembly" +
          s"s default lifecyclestate  is ${assemblyState}  and operational state is : ${assemblyMotionState}"
        )
        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, loggerFactory)
      }
      case HCDState_Off => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so changing lifecycle state of Assembly to shutdown " +
          s"and operational state to : disconnected "
        )
        eventHandlerActor ! PublishEvent(
          eventTransformer
            .getAssemblyEvent(AssemblyCurrentState(AssemblyLifeCycleState.Shutdown, AssemblyOperationalState.Disconnected))
        )
        MonitorActor.createObject(AssemblyLifeCycleState.Shutdown,
                                  AssemblyOperationalState.Disconnected,
                                  eventHandlerActor,
                                  loggerFactory)
      }
      case _ => {
        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, loggerFactory)
      }
    }
  }

  //TODO : here add logic for updating states from slewing --> tracking and vice-versa
  /* private def updateOperationalState(hcdOperationStateParam: Parameter[String]) = {
    val hcdOperationalState = hcdOperationStateParam.head
    if (hcdOperationalState == "ServoOffDatumed" || hcdOperationalState == "ServoOffDrivePowerOn") {
      log.info(
        msg =
          s"Updated operational state of assembly  on receipt of hcd operataional state : ${hcdOperationalState} is ${AssemblyOperationalState.Running}"
      )
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running, loggerFactory)

    } else if (hcdOperationalState == "Following") {
      log.info(
        msg =
          s"Updated operational state of assembly  on receipt of hcd operataional state : ${hcdOperationalState} is ${AssemblyOperationalState.Slewing}"
      )
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Slewing, loggerFactory) //slewing or tracking

    } else if (hcdOperationalState == "PointingDatumed" || hcdOperationalState == "PointingDrivePowerOn") {
      log.info(
        msg =
          s"Updated operational state of assembly  on receipt of hcd operataional state : ${hcdOperationalState} is ${AssemblyOperationalState.Slewing}"
      )
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Slewing, loggerFactory) //slewing or tracking

    } else {
      MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)

    }

  }*/

  /*
      TODO : Ask what to do on finding hcd location
   */
  def onLocationEvent(hcdLocation: Option[CommandService]): Behavior[MonitorMessage] = {
    hcdLocation match {
      case Some(_) => {
        log.info(msg = s"Found HCD location at ${hcdLocation}")
        if (assemblyState == AssemblyLifeCycleState.RunningOffline) {
          MonitorActor.createObject(AssemblyLifeCycleState.Running, assemblyMotionState, eventHandlerActor, loggerFactory)
        } else {
          Behavior.same
        }
      }
      case None => {
        log.error("Assembly got disconnected from HCD")
        MonitorActor.createObject(AssemblyLifeCycleState.RunningOffline, assemblyMotionState, eventHandlerActor, loggerFactory)
      }
    }
  }
}
