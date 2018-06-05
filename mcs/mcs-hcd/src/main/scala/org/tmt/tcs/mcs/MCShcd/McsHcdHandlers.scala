package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentHandlers, CurrentStatePublisher}
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.commands.CommandIssue.{UnsupportedCommandIssue, WrongInternalStateIssue, WrongNumberOfParametersIssue}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.generics.Parameter
import csw.messages.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage.{GetCurrentState, HCDLifeCycleStateChangeMsg, HCDOperationalStateChangeMsg}
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.{InitializeMsg, ShutdownMsg}
import org.tmt.tcs.mcs.MCShcd.constants.Commands
//import akka.pattern.ask
import scala.concurrent.{ExecutionContextExecutor, Future}

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
    loggerFactory: LoggerFactory
) extends ComponentHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory) {

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  private val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, locationService, loggerFactory), "LifeCycleActor")
  private val eventHandlerActor: ActorRef[EventMessage] = ctx.spawn(
    EventHandlerActor.createObject(currentStatePublisher,
                                   HCDLifeCycleState.Off,
                                   HCDOperationalState.DrivePowerOff,
                                   loggerFactory),
    "EventHandlerActor"
  )
  private val commandHandlerActor: ActorRef[ControlCommand] =
    ctx.spawn(CommandHandlerActor.createObject(commandResponseManager, loggerFactory), "CommandHandlerActor")
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS HCD")
    lifeCycleActor ! InitializeMsg()
    eventHandlerActor ! HCDLifeCycleStateChangeMsg(HCDLifeCycleState.Initialized)
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
      case x =>
        CommandResponse.Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }

  private def validatePointDemandCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = "validating point demand command in HCD")
    /*val azParam : Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "az").get
        val elParam : Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "el").get
        if(azParam.head == 0 || elParam.head == 0){
          CommandResponse.Invalid(controlCommand.runId, In(s"Command $x is not supported"))
        }*/
    val hcdCurrentState: Future[EventMessage.HcdCurrentState] = eventHandlerActor ! GetCurrentState()
    hcdCurrentState map {
      case msg: EventMessage.HcdCurrentState => {
        if ((msg.operationalState == HCDOperationalState.PointingDatumed || msg.operationalState == HCDOperationalState.PointingDrivePowerOn)
            && msg.lifeCycleState == HCDLifeCycleState.Running) {
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
      }
    }
  }

  private def validatePointCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = "Validating point command in HCD")
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param              = axes.head
    //TODO : set drive power on during startup command

    if (param == "BOTH" || param == "AZ" || param == "EL") {
      val hcdCurrentState: Future[EventMessage.HcdCurrentState] = eventHandlerActor ? GetCurrentState()
      hcdCurrentState map {
        case msg: EventMessage.HcdCurrentState => {
          if ((msg.operationalState == HCDOperationalState.ServoOffDrivePowerOn || msg.operationalState == HCDOperationalState.ServoOffDatumed)
              && msg.lifeCycleState == HCDLifeCycleState.Running) {
            CommandResponse.Accepted(controlCommand.runId)
          } else {
            CommandResponse.Invalid(
              controlCommand.runId,
              WrongInternalStateIssue(
                s" MCS HCD and subsystem is not in running state " +
                s"and operational state must be servoOffDrivePowerOn or servoOffDatumed to process point  command"
              )
            )
          }
        }
      }

    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for point command"))
    }
  }

  private def validateDatumCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating Datum command in HCD")
    //TODO : Below validations  to be added
    //1. HCD must be in running state --> check whether HCD can communicate with MCS subsystem
    //2. drive power must be on
    //3. check for paramters :- done
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param              = axes.head
    //TODO : set drive power on during startup command

    if (param == "BOTH" || param == "AZ" || param == "EL") {
      val hcdCurrentState: Future[EventMessage.HcdCurrentState] = eventHandlerActor ? GetCurrentState()
      hcdCurrentState map {
        case msg: EventMessage.HcdCurrentState => {
          if (msg.operationalState == HCDOperationalState.ServoOffDrivePowerOn && msg.lifeCycleState == HCDLifeCycleState.Running) {
            CommandResponse.Accepted(controlCommand.runId)
          } else {
            CommandResponse.Invalid(
              controlCommand.runId,
              WrongInternalStateIssue(
                s" MCS HCD and subsystem is not in running state " +
                s"and operational state must be servoOffDrivePowerOn to process datum  command"
              )
            )
          }
        }
      }

    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }
  }

  private def validateFollowCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating follow command in HCD")
    if (controlCommand.paramSet.isEmpty) {
      val hcdCurrentState: Future[EventMessage.HcdCurrentState] = eventHandlerActor ! GetCurrentState()
      hcdCurrentState map {
        case msg: EventMessage.HcdCurrentState => {
          if (msg.operationalState == HCDOperationalState.ServoOffDatumed && msg.lifeCycleState == HCDLifeCycleState.Running) {
            CommandResponse.Accepted(controlCommand.runId)
          } else {
            CommandResponse.Invalid(
              controlCommand.runId,
              WrongInternalStateIssue(
                s" MCS HCD and subsystem is not in running state " +
                s"and operational state must be servoOffDatumed to process follow command"
              )
            )
          }
        }
        case _ => {
          CommandResponse.Invalid(
            controlCommand.runId,
            WrongInternalStateIssue(
              s" MCS HCD and subsystem is not in running state " +
              s"and operational state must be servoOffDatumed to process follow command"
            )
          )
        }
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId, WrongNumberOfParametersIssue("Follow command should not have any parameters"))

    }
  }

  override def onSubmit(controlCommand: ControlCommand): Unit = {

    log.info(msg = "Command submitted to HCD in onSubmit wrapper")
    commandHandlerActor ! controlCommand
    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("On receipt of startup command changing MCS HCD state to Running")
        eventHandlerActor ! HCDLifeCycleStateChangeMsg(HCDLifeCycleState.Running)
        eventHandlerActor ! HCDOperationalStateChangeMsg(HCDOperationalState.DrivePowerOff)
      }
      case Commands.SHUTDOWN => {
        log.info("On receipt of shutdown command changing MCS HCD state to Disconnected")
        eventHandlerActor ! HCDLifeCycleStateChangeMsg(HCDLifeCycleState.Disconnected)
      }
      case Commands.DATUM => {
        log.info("changing HCD's operational state to ServoOffDatumed")
        eventHandlerActor ! HCDOperationalStateChangeMsg(HCDOperationalState.ServoOffDatumed)
      }
      case Commands.FOLLOW => {
        //TODO : how to decide whether it is slewing or tracking in following state business logic for the same..?
        log.info("changing HCD's operational state to following")
        eventHandlerActor ! HCDOperationalStateChangeMsg(HCDOperationalState.Following)
      }
      case Commands.POINT | Commands.POINT_DEMAND => {
        log.info("changing HCD's operational state to pointing")
        val hcdCurrentState: Future[EventMessage.HcdCurrentState] = eventHandlerActor ? EventMessage.GetCurrentState
        hcdCurrentState map {
          case msg: EventMessage.HcdCurrentState => {
            if (msg.operationalState == HCDOperationalState.ServoOffDatumed) {
              eventHandlerActor ! HCDOperationalStateChangeMsg(HCDOperationalState.PointingDatumed)
            } else if (msg.operationalState == HCDOperationalState.ServoOffDrivePowerOn) {
              eventHandlerActor ! HCDOperationalStateChangeMsg(HCDOperationalState.PointingDrivePowerOn)
            }
          }
        }
      }

    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.info(msg = "Command submmited to HCD in onOneway wrapper")
  }

  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS HCD")
    lifeCycleActor ! ShutdownMsg()
    eventHandlerActor ! HCDLifeCycleStateChangeMsg(HCDLifeCycleState.Off)
  }

  override def onGoOffline(): Unit = {
    log.info(msg = "MCS HCD going offline")
  }

  override def onGoOnline(): Unit = {
    log.info(msg = "MCS HCD going online")
  }

}
