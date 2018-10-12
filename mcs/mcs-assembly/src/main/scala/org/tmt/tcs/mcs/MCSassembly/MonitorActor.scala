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
import org.tmt.tcs.mcs.MCSassembly.EventMessage.PublishHCDState
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

}
object MonitorActor {
  def createObject(assemblyState: AssemblyLifeCycleState.AssemblyState,
                   assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
                   eventHandlerActor: ActorRef[EventMessage],
                   eventTransformer: EventTransformerHelper,
                   loggerFactory: LoggerFactory): Behavior[MonitorMessage] =
    Behaviors.setup(
      ctx => MonitorActor(ctx, assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
    )

}
/*
This actor is responsible for maintaing state of MCS assembly
 */
case class MonitorActor(ctx: ActorContext[MonitorMessage],
                        assemblyState: AssemblyLifeCycleState.AssemblyState,
                        assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
                        eventHandlerActor: ActorRef[EventMessage],
                        eventTransformer: EventTransformerHelper,
                        loggerFactory: LoggerFactory)
    extends MutableBehavior[MonitorMessage] {

  private val log = loggerFactory.getLogger

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
        // log.info(s"Current lifeCycle state of assembly is : ${assemblyState} and operational state is : ${assemblyMotionState}")
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

    MonitorActor.createObject(x.assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
  }
  /*
 This function updates assembly operational state
   */
  def onAssemblyOperationalStateChangeMsg(x: MonitorMessage with AssemblyOperationalStateChangeMsg): Behavior[MonitorMessage] = {
    log.info(msg = s"Successfully changed monitor actor state to ${x.assemblyMotionState}")

    MonitorActor.createObject(assemblyState, x.assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
  }
  /*
  This function receives hcd lifecycle state, current position and other current states
   amd accordingly derives assembly operational state and publishes HCD current states to eventHandler Actor
   for publishing to other TCS Assemblies
   Also this actor sends current states received from HCD to EventHandlerActor for publishing
   */
  def onCurrentStateChange(x: MonitorMessage with currentStateChangeMsg): Behavior[MonitorMessage] = {

    val currentState: CurrentState = x.currentState
    //log.info(msg = s"Received currentState from HCD")
    currentState.stateName.name match {
      case HCDLifecycleState => {
        //log.info("Received life cycle state change message from HCD updating state of assembly corresponding to change")
        updateAssemblyState(currentState)
      }
      case CURRENT_POSITION => {

        //processMCSCurrentPositionEvent(currentState)
        val currentPosition = eventTransformer.getCurrentPositionEvent(currentState)
        //log.info(s"** Processing currentPosition received from HCD to : ${eventHandlerActor}**")
        eventHandlerActor ! PublishHCDState(currentPosition)
        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
      }
      case DIAGNOSIS_STATE => {
        eventHandlerActor ! PublishHCDState(eventTransformer.getDiagnosisEvent(currentState))
        Behavior.same
      }
      case HEALTH_STATE => {

        val health = eventTransformer.getHealthEvent(currentState)

        //log.info(s"** Processing health received from HCD to : ${eventHandlerActor}**")
        eventHandlerActor ! PublishHCDState(health)

        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
      }
      case DRIVE_STATE => {
        eventHandlerActor ! PublishHCDState(eventTransformer.getDriveState(currentState))
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

        val assemblyCurrentState: AssemblyCurrentState =
          AssemblyCurrentState(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running)
        val assemblyStateEvent = eventTransformer.getAssemblyEvent(assemblyCurrentState)

        eventHandlerActor ! PublishHCDState(assemblyStateEvent)
        MonitorActor.createObject(AssemblyLifeCycleState.Running,
                                  AssemblyOperationalState.Running,
                                  eventHandlerActor,
                                  eventTransformer,
                                  loggerFactory)
      }
      case HCDState_Initialized => {
        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
      }
      case HCDState_Off => {

        eventHandlerActor ! PublishHCDState(
          eventTransformer
            .getAssemblyEvent(AssemblyCurrentState(AssemblyLifeCycleState.Shutdown, AssemblyOperationalState.Disconnected))
        )
        MonitorActor.createObject(AssemblyLifeCycleState.Shutdown,
                                  AssemblyOperationalState.Disconnected,
                                  eventHandlerActor,
                                  eventTransformer,
                                  loggerFactory)
      }
      case _ => {
        log.error(
          s"********************** Unknown HCD State received to MonitorActor ************** state is : ${hcdLifeCycleState}"
        )
        MonitorActor.createObject(assemblyState, assemblyMotionState, eventHandlerActor, eventTransformer, loggerFactory)
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

  def onLocationEvent(hcdLocation: Option[CommandService]): Behavior[MonitorMessage] = {
    hcdLocation match {
      case Some(_) => {
        if (assemblyState == AssemblyLifeCycleState.RunningOffline) {
          MonitorActor.createObject(AssemblyLifeCycleState.Running,
                                    assemblyMotionState,
                                    eventHandlerActor,
                                    eventTransformer,
                                    loggerFactory)
        } else {
          Behavior.same
        }
      }
      case None => {
        log.error("Assembly got disconnected from HCD")
        MonitorActor.createObject(AssemblyLifeCycleState.RunningOffline,
                                  assemblyMotionState,
                                  eventHandlerActor,
                                  eventTransformer,
                                  loggerFactory)
      }
    }
  }
}
