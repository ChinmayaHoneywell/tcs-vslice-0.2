package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.params.generics.{KeyType, Parameter}
import csw.messages.params.states.CurrentState
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage._

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
                   loggerFactory: LoggerFactory): Behavior[MonitorMessage] =
    Behaviors.setup(ctx => MonitorActor(ctx, assemblyState, assemblyMotionState, loggerFactory))

}
/*
This actor is responsible for maintaing state of MCS assembly
 */
case class MonitorActor(ctx: ActorContext[MonitorMessage],
                        assemblyState: AssemblyLifeCycleState.AssemblyState,
                        assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
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
    MonitorActor.createObject(x.assemblyState, assemblyMotionState, loggerFactory)
  }
  /*
 This function updates assembly operational state
   */
  def onAssemblyOperationalStateChangeMsg(x: MonitorMessage with AssemblyOperationalStateChangeMsg): Behavior[MonitorMessage] = {
    log.info(msg = s"Successfully changed monitor actor state to ${x.assemblyMotionState}")
    MonitorActor.createObject(assemblyState, x.assemblyMotionState, loggerFactory)
  }
  /*
  This functiona receives hcd lifecycle state and current position and accordingly derives assembly operational state
   */
  def onCurrentStateChange(x: MonitorMessage with currentStateChangeMsg): Behavior[MonitorMessage] = {

    val currentState: CurrentState = x.currentState
    log.info(msg = s"Received state change msg from HCD")

    def handleLifeCycleChange: Behavior[MonitorMessage] = {
      val optHcdLifeCycleStateParam: Option[Parameter[String]] = currentState.get("hcdLifeCycleState", KeyType.StringKey)
      optHcdLifeCycleStateParam match {
        case Some(hcdLifeCycleStateParam) => {

          updateLifeCycleState(hcdLifeCycleStateParam)
        }
        case None => {
          log.info(msg = "Some incorrect information is received in hcdLifecycle state change msg")
          Behavior.unhandled
        }
      }
    }

    currentState.stateName.name match {
      case "lifecycleState" => {
        log.info("Received life cycle state change message from HCD updating state of assembly correcsponding to change")
        handleLifeCycleChange
      }
      case "currentPosition" => {
        //TODO : here should be the logic to change assembly states based on currentPosition such as slewing,tracking
        MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)
      }
    }

  }
  /*
This function updates assembly,lifecycle state as per hcd's lifecycle state
   */
  private def updateLifeCycleState(hcdLifeCycleStateParam: Parameter[String]) = {
    log.info(msg = s"Received state change message from HCD so updating Assembly state")

    val hcdLifeCycleState = hcdLifeCycleStateParam.head
    hcdLifeCycleState match {
      case "Running" => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so changing lifecycle state of Assembly to Running "
        )
        MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running, loggerFactory)
      }
      case "Initialized" => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so not changing lifecycle state of Assembly assembly" +
          s"s default lifecyclestate  is ${assemblyState}  and operational state is : ${assemblyMotionState}"
        )
        MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)
      }
      case "Off" => {
        log.info(
          msg = s"Received life cycle state of HCD is : ${hcdLifeCycleState} so changing lifecycle state of Assembly to shutdown " +
          s"and operational state to : disconnected "
        )
        MonitorActor.createObject(AssemblyLifeCycleState.Shutdown, AssemblyOperationalState.Disconnected, loggerFactory)
      }
      case _ => {
        MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)
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
          MonitorActor.createObject(AssemblyLifeCycleState.Running, assemblyMotionState, loggerFactory)
        } else {
          Behavior.same
        }
      }
      case None => {
        log.error("Assembly got disconnected from HCD")
        MonitorActor.createObject(AssemblyLifeCycleState.RunningOffline, assemblyMotionState, loggerFactory)
      }
    }
  }
}
