package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import csw.framework.scaladsl.ComponentHandlers
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.commands.CommandIssue.{UnsupportedCommandIssue, WrongInternalStateIssue, WrongNumberOfParametersIssue}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.generics.Parameter
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage.{publishCurrentPosition, HCDOperationalStateChangeMsg, StateChangeMsg}
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.{InitializeMsg, ShutdownMsg}
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import akka.actor.typed.scaladsl.AskPattern._
import csw.framework.CurrentStatePublisher
import csw.messages.TopLevelActorMessage
import csw.services.command.CommandResponseManager

import scala.concurrent.Await
import scala.concurrent.duration._
//import akka.pattern.ask
import scala.concurrent.{ExecutionContextExecutor, Future}
import csw.services.event.scaladsl.EventService

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to McsHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
class McsHcdHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    componentInfo: ComponentInfo,
    commandResponseManager: CommandResponseManager,
    currentStatePublisher: CurrentStatePublisher,
    locationService: LocationService,
    eventService: EventService,
    loggerFactory: LoggerFactory
) extends ComponentHandlers(ctx,
                              componentInfo,
                              commandResponseManager,
                              currentStatePublisher,
                              locationService,
                              eventService,
                              loggerFactory) {

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  private val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, locationService, loggerFactory), "LifeCycleActor")
  private val statePublisherActor: ActorRef[EventMessage] = ctx.spawn(
    StatePublisherActor.createObject(currentStatePublisher,
                                     HCDLifeCycleState.Off,
                                     HCDOperationalState.DrivePowerOff,
                                     loggerFactory),
    "StatePublisherActor"
  )
  private val commandHandlerActor: ActorRef[ControlCommand] =
    ctx.spawn(CommandHandlerActor.createObject(commandResponseManager, loggerFactory), "CommandHandlerActor")
  /*
  This function initializes HCD and intilizes Lifecycle actor and statepublisher actor
  with initialized state
   */
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS HCD")
    lifeCycleActor ! InitializeMsg()
    statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Initialized, HCDOperationalState.DrivePowerOff)
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info(msg = "Location event changed for MCD HCD")
  }

  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = s" validating command ----> ${controlCommand.commandName}")
    controlCommand.commandName.name match {
      case Commands.DATUM => {
        validateDatumCommand(controlCommand)
      }
      case Commands.FOLLOW => {
        validateFollowCommand(controlCommand)

      }
      case Commands.POINT => {
        validatePointCommand(controlCommand)
      }
      case Commands.POINT_DEMAND => {
        validatePointDemandCommand(controlCommand)
      }
      case Commands.STARTUP => {
        log.info(msg = s"validating startup command in HCD")
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.SHUTDOWN => {
        log.info(msg = s"validating shutdown command in HCD")
        CommandResponse.Accepted(controlCommand.runId)
      }
      case x =>
        CommandResponse.Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }
  /*
     This functions validates point demand command based upon paramters and hcd state
    */
  private def validatePointDemandCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = s"validating point demand command in HCD")

    def validateParams: Boolean = {
      val azParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "AZ").get
      val param1                = azParam.head
      val elParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "EL").get
      val param2                = elParam.head
      log.info(msg = s"In Point Demand command az value is : ${param1} and el value is : ${param2}")
      if (param1 == null || param2 == null) {
        return false
      }
      return true
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match { //TODO : here should be the logic to change assembly states based on current state
        case x: EventMessage.HcdCurrentState => {
          x.lifeCycleState match {
            case HCDLifeCycleState.Running
                if x.operationalState.equals(HCDOperationalState.PointingDrivePowerOn) ||
                x.operationalState.equals(HCDOperationalState.PointingDatumed) => {
              return true
            }
            case _ => {
              log.error(
                msg = s" to execute pointind demand command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.PointingDatumed} or ${HCDOperationalState.PointingDrivePowerOn}"
              )
              return false
            }
          }
        }
        case _ => {
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
        }
      }
      return false
    }
    if (validateParams) {
      if (validateHCDState) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem is not in running state " +
            s"and operational state must be PointingDatumed or PointingDrivePowerOn to process pointDemand command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" az and el parameter is not provided for point demand command"))
    }

  }
  /*
       This functions validates point  command based upon paramters and hcd state
      */
  private def validatePointCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = "Validating point command in HCD")
    def validateParams: Boolean = {
      val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
      val param1                  = axesParam.head
      if (param1 == "BOTH" || param1 == "AZ" || param1 == "EL") {
        return true
      }
      return false
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState => {
          x.lifeCycleState match {
            case HCDLifeCycleState.Running
                if x.operationalState.equals(HCDOperationalState.ServoOffDrivePowerOn) ||
                x.operationalState.equals(HCDOperationalState.ServoOffDatumed) => {
              return true
            }
            case _ => {
              log.error(
                msg = s" to execute pointind demand command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.ServoOffDrivePowerOn} or ${HCDOperationalState.ServoOffDatumed}"
              )
              return false
            }
          }
        }
        case _ => {
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
        }
      }
      return false
    }
    if (validateParams) {
      if (validateHCDState) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem must be in ${HCDLifeCycleState.Running} state" +
            s"and operational state must be ${HCDOperationalState.ServoOffDrivePowerOn} or ${HCDOperationalState.ServoOffDatumed} to process point  command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for point command"))
    }
  }
  /*
       This functions validates datum  command based upon paramters and hcd state
      */
  private def validateDatumCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating Datum command in HCD")
    def validateParams: Boolean = {
      val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
      val param1                  = axesParam.head
      if (param1 == "BOTH" || param1 == "AZ" || param1 == "EL") {
        return true
      }
      return false
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState => {
          x.lifeCycleState match {
            case HCDLifeCycleState.Running if x.operationalState.equals(HCDOperationalState.DrivePowerOff) => {
              return true
            }
            case _ => {
              log.error(
                msg = s" to execute datum command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.DrivePowerOff} "
              )
              return false
            }
          }
        }
        case _ => {
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
        }
      }
      return false
    }
    if (validateParams) {
      if (validateHCDState) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem must be  in ${HCDLifeCycleState.Running} state " +
            s"and operational state must be ${HCDOperationalState.ServoOffDrivePowerOn} or  ${HCDOperationalState.ServoOffDatumed} " +
            s"to process datum  command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }

  }
  /*
       This functions validates folow  command based upon paramters and hcd state
      */
  private def validateFollowCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating follow command in HCD")
    def validateParamset: Boolean = {
      return controlCommand.paramSet.isEmpty
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState => {
          x.lifeCycleState match {
            case HCDLifeCycleState.Running if x.operationalState.equals(HCDOperationalState.ServoOffDatumed) => {
              return true
            }
            case _ => {
              log.error(
                msg = s" to execute Follow command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.ServoOffDatumed}"
              )
              return false
            }
          }
        }
        case _ => {
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
        }
      }
      return false
    }
    if (validateParamset) {
      if (validateHCDState) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem must be in ${HCDLifeCycleState.Running} state" +
            s"and operational state must be in ${HCDOperationalState.ServoOffDatumed} to process follow command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId, WrongNumberOfParametersIssue("Follow command should not have any parameters"))
    }
  }
  /*
       This functions routes all commands to commandhandler actor and bsed upon command execution updates states of HCD by sending it
       to statepublisher actor
      */
  override def onSubmit(controlCommand: ControlCommand): Unit = {

    log.info(msg = "Command submitted to HCD in onSubmit wrapper")
    commandHandlerActor ! controlCommand
    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("On receipt of startup command changing MCS HCD state to Running")
        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Running, HCDOperationalState.DrivePowerOff)
      }
      case Commands.SHUTDOWN => {
        log.info("On receipt of shutdown command changing MCS HCD state to Disconnected")
        //TODO: send to subsystem
        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Off, HCDOperationalState.Disconnected)
      }
      case Commands.DATUM => {
        log.info("changing HCD's operational state to ServoOffDatumed")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.ServoOffDatumed)
      }
      case Commands.FOLLOW => {
        //TODO : how to decide whether it is slewing or tracking in following state business logic for the same..?
        log.info("changing HCD's operational state to following")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.Following)
        statePublisherActor ! publishCurrentPosition()
      }
      case Commands.POINT | Commands.POINT_DEMAND => {
        log.info("changing HCD's operational state to pointing")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.PointingDatumed)
        statePublisherActor ! publishCurrentPosition()

      }

    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.info(msg = "Command submmited to HCD in onOneway wrapper")
  }

  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS HCD")
    lifeCycleActor ! ShutdownMsg()
    statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Off, HCDOperationalState.Disconnected)
  }

  override def onGoOffline(): Unit = {
    log.info(msg = "MCS HCD going offline")
  }

  override def onGoOnline(): Unit = {
    log.info(msg = "MCS HCD going online")
  }

}
