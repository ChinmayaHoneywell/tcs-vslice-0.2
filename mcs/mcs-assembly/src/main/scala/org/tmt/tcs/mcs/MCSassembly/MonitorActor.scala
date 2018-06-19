package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
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
    Behaviors.mutable(ctx => MonitorActor(ctx, assemblyState, assemblyMotionState, loggerFactory))

}
case class MonitorActor(ctx: ActorContext[MonitorMessage],
                        assemblyState: AssemblyLifeCycleState.AssemblyState,
                        assemblyMotionState: AssemblyOperationalState.AssemblyMotionState,
                        loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[MonitorMessage] {

  private val log = loggerFactory.getLogger

  override def onMessage(msg: MonitorMessage): Behavior[MonitorMessage] = {
    msg match {
      case x: AssemblyLifeCycleStateChangeMsg   => onAssemblyLifeCycleStateChangeMsg(x)
      case x: AssemblyOperationalStateChangeMsg => onAssemblyOperationalStateChangeMsg(x)
      case x: LocationEventMsg                  => onLocationEvent(x.hcdLocation)
      case x: currentStateChangeMsg             => onCurrentStateChange(x)
      case x: GetCurrentState => {
        log.info(s"Current lifeCycle state of assembly is  ${assemblyState} and operational state is  ${assemblyMotionState}")
        x.actorRef ! AssemblyCurrentState(assemblyState, assemblyMotionState)
        this
      }
      case _ => {
        log.error(msg = s"Incorrect message $msg is sent to MonitorActor")
        Behavior.unhandled
      }
    }
    // this
  }
  def onAssemblyLifeCycleStateChangeMsg(x: MonitorMessage with AssemblyLifeCycleStateChangeMsg): Behavior[MonitorMessage] = {
    MonitorActor.createObject(x.assemblyState, assemblyMotionState, loggerFactory)
    log.info(msg = s"Successfully changed monitor assembly lifecycle state to ${x.assemblyState}")
    this
  }
  def onAssemblyOperationalStateChangeMsg(x: MonitorMessage with AssemblyOperationalStateChangeMsg): Behavior[MonitorMessage] = {
    MonitorActor.createObject(assemblyState, x.assemblyMotionState, loggerFactory)
    log.info(msg = s"Successfully changed monitor actor state to ${x.assemblyMotionState}")
    this
  }
  def onCurrentStateChange(x: MonitorMessage with currentStateChangeMsg): Behavior[MonitorMessage] = {
    //TODO : here should be the logic to change assembly states based on current state
    val currentState: CurrentState                           = x.currentState
    val optHcdLifeCycleStateParam: Option[Parameter[String]] = currentState.get("hcdLifeCycleState", KeyType.StringKey)
    val optHcdOperationStateParam: Option[Parameter[String]] = currentState.get("hcdOperationalState", KeyType.StringKey)
    optHcdLifeCycleStateParam match {
      case Some(hcdLifeCycleStateParam) => {
        log.info(msg = s"Sent HCD LifeCycle state param : ${hcdLifeCycleStateParam} so updating hcdLifeCycle state param")
        updateLifeCycleState(hcdLifeCycleStateParam)
      }
      case None => {
        log.info(msg = " HCD lifecycle state param is not sent so not updating lifecycle state")
      }
    }
    optHcdOperationStateParam match {
      case Some(hcdOperationStateParam) => {
        log.info(msg = s"Sent HCD operational state param : ${hcdOperationStateParam} so updating hcdOperational state param")
        updateOperationalState(hcdOperationStateParam)
      }
      case None => {
        log.info(msg = " HCD Operational state param is not sent so not updating HCD operational state")
      }
    }

    this
  }

  private def updateLifeCycleState(hcdLifeCycleStateParam: Parameter[String]) = {
    val hcdLifeCycleState = hcdLifeCycleStateParam.head
    if (hcdLifeCycleState == "Running") {
      // on startup command
      log.info(
        msg =
          s"Updated life cycle state of assembly  on receipt of lifecycle state : ${hcdLifeCycleState} is ${AssemblyLifeCycleState.Running}"
      )
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running, loggerFactory)
      // this
    } else if (hcdLifeCycleState == "Initialized") {
      // this is for initializtion hook
      log.info(
        msg =
          s"Updated life cycle state of assembly  on receipt of lifecycle state : ${hcdLifeCycleState} is ${AssemblyLifeCycleState.Initalized}"
      )
      MonitorActor.createObject(AssemblyLifeCycleState.Initalized, AssemblyOperationalState.Ready, loggerFactory) //check whether assembly operational state should be ready or not
      //this
    } else {
      MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)

    }

  }
  //TODO : here add logic for updating states from slewing --> tracking and vice-versa
  private def updateOperationalState(hcdOperationStateParam: Parameter[String]) = {
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

  }

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
          this
        }
      }
      case None => {
        log.error("Assembly got disconnected from HCD")
        MonitorActor.createObject(AssemblyLifeCycleState.RunningOffline, assemblyMotionState, loggerFactory)
      }
    }
  }
}
