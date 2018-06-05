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
    log.info(msg = s"Successfully changed monitor assembly lifecycle state to $x.assemblyState")
    this
  }
  def onAssemblyOperationalStateChangeMsg(x: MonitorMessage with AssemblyOperationalStateChangeMsg): Behavior[MonitorMessage] = {
    MonitorActor.createObject(assemblyState, x.assemblyMotionState, loggerFactory)
    log.info(msg = s"successfully changed monitor actor state to $x.assemblyMotionState")
    this
  }
  def onCurrentStateChange(x: MonitorMessage with currentStateChangeMsg): Behavior[MonitorMessage] = {
    //TODO : here should be the logic to change assembly states based on current state
    val currentState: CurrentState = x.currentState

    updateOperationalState(currentState)

    updateLifeCycleState(currentState)
    this
  }

  private def updateLifeCycleState(currentState: CurrentState) = {
    val hcdLifeCycleStateParam: Option[Parameter[String]] = currentState.get("hcdLifeCycleState", KeyType.StringKey)
    val hcdLifeCycleState                                 = hcdLifeCycleStateParam.get.head
    if (hcdLifeCycleState == "Running") {
      // on startup command
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running, loggerFactory)
      // this
    } else if (hcdLifeCycleState == "Initialized") {
      // this is for initializtion hook
      MonitorActor.createObject(AssemblyLifeCycleState.Initalized, AssemblyOperationalState.Ready, loggerFactory) //check whether assembly operational state should be ready or not
      //this
    } else {
      MonitorActor.createObject(assemblyState, assemblyMotionState, loggerFactory)

    }

  }

  private def updateOperationalState(currentState: CurrentState) = {
    val hcdOperationStateParam: Option[Parameter[String]] = currentState.get("hcdOperationalState", KeyType.StringKey)
    val hcdOperationalState                               = hcdOperationStateParam.get.head
    if (hcdOperationalState == "ServoOffDatumed" || hcdOperationalState == "ServoOffDrivePowerOn") {
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Running, loggerFactory)

    } else if (hcdOperationalState == "Following") {
      MonitorActor.createObject(AssemblyLifeCycleState.Running, AssemblyOperationalState.Slewing, loggerFactory) //slewing or tracking

    } else if (hcdOperationalState == "PointingDatumed" || hcdOperationalState == "PointingDrivePowerOn") {
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
        log.info(msg = s"Found HCD location at $hcdLocation")
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
