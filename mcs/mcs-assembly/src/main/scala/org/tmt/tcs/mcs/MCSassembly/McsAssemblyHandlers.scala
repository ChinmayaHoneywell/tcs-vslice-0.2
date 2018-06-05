package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentHandlers, CurrentStatePublisher}
import csw.messages.commands.{CommandName, CommandResponse, ControlCommand}
import csw.messages.framework.ComponentInfo
import csw.messages.location.{AkkaLocation, LocationUpdated, TrackingEvent}
import csw.messages.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.{CommandResponseManager, CommandService, CurrentStateSubscription}
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import csw.messages.commands.CommandIssue.{UnsupportedCommandInStateIssue, UnsupportedCommandIssue, WrongNumberOfParametersIssue}
import csw.messages.params.generics.Parameter
import org.tmt.tcs.mcs.MCSassembly.CommandMessage.{submitCommandMsg, updateHCDLocation, GoOfflineMsg, GoOnlineMsg}
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.{InitializeMsg, ShutdownMsg}
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage.{currentStateChangeMsg, GetCurrentState, LocationEventMsg}

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to McsHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
class McsAssemblyHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    componentInfo: ComponentInfo,
    commandResponseManager: CommandResponseManager,
    currentStatePublisher: CurrentStatePublisher,
    locationService: LocationService,
    loggerFactory: LoggerFactory
) extends ComponentHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory) {

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, locationService, loggerFactory), "LifeCycleActor")
  val monitorActor: ActorRef[MonitorMessage] =
    ctx.spawn(MonitorActor.createObject(AssemblyLifeCycleState.Initalized, AssemblyOperationalState.Ready, loggerFactory),
              name = "MonitorActor")
  val eventHandlerActor: ActorRef[EventMessage] =
    ctx.spawn(EventHandlerActor.createObject(loggerFactory), name = "EventHandlerActor")
  var hcdStateSubscriber: Option[CurrentStateSubscription] = None
  var hcdLocation: Option[CommandService]                  = None
  val commandHandlerActor: ActorRef[CommandMessage] = ctx.spawn(
    CommandHandlerActor.createObject(commandResponseManager, isOnline = true, hcdLocation, loggerFactory),
    "CommandHandlerActor"
  )

  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS Assembly")
    lifeCycleActor ! InitializeMsg()

  }
  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS Assembly")
    lifeCycleActor ! ShutdownMsg()
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info(msg = s"Location Tracking event changed $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) => {
        hcdLocation = Some(new CommandService(location.asInstanceOf[AkkaLocation])(ctx.system))
        hcdStateSubscriber = Some(hcdLocation.get.subscribeCurrentState(monitorActor ! currentStateChangeMsg(_)))
      }
      case _ => {
        hcdLocation = None
      }
    }
    monitorActor ! LocationEventMsg(hcdLocation)
    log.info(msg = s"Sent hcd location : $hcdLocation to monitorActor for update")
    commandHandlerActor ! updateHCDLocation(hcdLocation)
    log.info(msg = s"Sent hcd location : $hcdLocation to commandHandlerActor for update")

  }

  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = s" validating command ----> ${controlCommand.commandName}")
    controlCommand.commandName.name match {

      case Commands.ELEVATIONSTOW => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.READCONFIGURATION => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.CANCELPROCESSING => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.SETDIAGNOSTICS => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.RESET => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.SERVO_OFF => {
        //TODO : Add validation logic here
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.FOLLOW => {
        validateFollowCommand(controlCommand)
      }
      case Commands.MOVE => {

        validateMoveCommand(controlCommand)
      }
      case Commands.DATUM => {

        validateDatumCommand(controlCommand)

      }
      case Commands.AXIS => {
        CommandResponse.Accepted(controlCommand.runId)
      }
      case x =>
        CommandResponse.Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }
  private def validateFollowCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating Follow Command")
    val assemblyCurrentState: MonitorMessage.AssemblyCurrentState = monitorActor ! GetCurrentState()
    if (assemblyCurrentState.lifeCycleState == AssemblyLifeCycleState.Running) {
      CommandResponse.Accepted(controlCommand.runId)
    } else {
      CommandResponse.Invalid(
        controlCommand.runId,
        UnsupportedCommandInStateIssue(s" Follow command is not allowed if assembly is not in Running state")
      )
    }
  }

  private def validateMoveCommand(controlCommand: ControlCommand): CommandResponse = {
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    log.info(s"axes value is $axes")
    val param = axes.head
    if (param == "BOTH" || param == "AZ" || param == "EL") {
      val assemblyCurrentState: MonitorMessage.AssemblyCurrentState = monitorActor ! GetCurrentState()
      if (assemblyCurrentState.lifeCycleState == AssemblyLifeCycleState.Running) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Move command is not allowed if assembly is not in Running state")
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }
  }

  private def validateDatumCommand(controlCommand: ControlCommand): CommandResponse = {
    // check hcd is in running state or in drive power on state
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param              = axes.head
    //  hcdLocation.get.

    if (param == "BOTH" || param == "AZ" || param == "EL") {
      val assemblyCurrentState: MonitorMessage.AssemblyCurrentState = monitorActor ! GetCurrentState()
      if (assemblyCurrentState.lifeCycleState == AssemblyLifeCycleState.Running) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Datum command is not allowed if assembly is not in Running state")
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }
  }

  override def onSubmit(controlCommand: ControlCommand): Unit = {
    log.info(msg = "Executing submit command in assembly")
    commandHandlerActor ! submitCommandMsg(controlCommand)
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.info(msg = "executing one way command")
  }

  //TODO : GoOnlineMSg is operational command..? why GoOnline and GoOffline messages are going to commandHandlerActor
  // If lifecycle commands then what they are supposed to do in LifecycleActor
  override def onGoOffline(): Unit = {
    log.info(msg = "MCS Assembly going down")
    commandHandlerActor ! GoOfflineMsg()
  }

  override def onGoOnline(): Unit = {
    log.info(msg = "MCS Assembly going online")
    commandHandlerActor ! GoOnlineMsg()
  }

}
