package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.framework.scaladsl.ComponentHandlers
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.framework.ComponentInfo
import csw.messages.location.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.services.command.scaladsl.{CommandService, CurrentStateSubscription}
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import csw.messages.commands.CommandIssue.{UnsupportedCommandInStateIssue, UnsupportedCommandIssue, WrongNumberOfParametersIssue}
import csw.messages.params.generics.Parameter
import org.tmt.tcs.mcs.MCSassembly.CommandMessage._
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.{InitializeMsg, ShutdownMsg}
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage._
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps

import scala.concurrent.duration._
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import akka.actor.typed.scaladsl.AskPattern._
import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.config.client.scaladsl.ConfigClientFactory
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import csw.framework.CurrentStatePublisher
import csw.messages.TopLevelActorMessage
import csw.services.command.CommandResponseManager
import csw.services.event.api.scaladsl.EventService
import org.tmt.tcs.mcs.MCSassembly.EventMessage.{hcdLocationChanged, StartEventSubscription, StartPublishingDummyEvent}

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
    eventService: EventService,
    loggerFactory: LoggerFactory
) extends ComponentHandlers(ctx,
                              componentInfo,
                              commandResponseManager,
                              currentStatePublisher,
                              locationService,
                              eventService,
                              loggerFactory) {


  implicit val ec: ExecutionContextExecutor                = ctx.executionContext
  private val log                                          = loggerFactory.getLogger
  private val configClient: ConfigClientService            = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  var hcdStateSubscriber: Option[CurrentStateSubscription] = None
  var hcdLocation: Option[CommandService]                  = None

  val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, configClient, loggerFactory), "LifeCycleActor")
  val eventHandlerActor: ActorRef[EventMessage] =
    ctx.spawn(EventHandlerActor.createObject(loggerFactory, hcdLocation, eventService), name = "EventHandlerActor")
  val monitorActor: ActorRef[MonitorMessage] =
    ctx.spawn(MonitorActor.createObject(AssemblyLifeCycleState.Initalized,
                                        AssemblyOperationalState.Ready,
                                        eventHandlerActor,
                                        loggerFactory),
              name = "MonitorActor")
  val commandHandlerActor: ActorRef[CommandMessage] = ctx.spawn(
    CommandHandlerActor.createObject(commandResponseManager, isOnline = true, hcdLocation, loggerFactory),
    "CommandHandlerActor"
  )

  /*
  This function is CSW in built initalization function
  1. It sends initializeMsg() to  LifecycleActor
  2. sends Initalized state msg to MonitorActor
  3. sends  StartPublishingEvents  and StartEventSubscription msg to EventHandlerActor
   */
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS Assembly")
    lifeCycleActor ! InitializeMsg()
    eventHandlerActor ! StartEventSubscription()

    eventHandlerActor ! StartPublishingDummyEvent()
    monitorActor ! AssemblyLifeCycleStateChangeMsg(AssemblyLifeCycleState.Initalized)
  }
  /*
  This function sends shutdown msg to lifecycle actor and updates Monitor actor status to shutdown
   */
  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS Assembly")
    monitorActor ! AssemblyLifeCycleStateChangeMsg(AssemblyLifeCycleState.Shutdown)
    lifeCycleActor ! ShutdownMsg()
  }
  /*
    This component tracks for updated hcd locations on command service and accordingly updates
    command handler actor and monitor actor
   */
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    trackingEvent match {
      case LocationUpdated(location) => {
        hcdLocation = Some(new CommandService(location.asInstanceOf[AkkaLocation])(ctx.system))
        hcdStateSubscriber = Some(hcdLocation.get.subscribeCurrentState(monitorActor ! currentStateChangeMsg(_)))
      }
      case LocationRemoved(_) => {
        hcdLocation = None
        log.error(s"Removing HCD Location registered with assembly")
      }
    }

    monitorActor ! LocationEventMsg(hcdLocation)
    commandHandlerActor ! updateHCDLocation(hcdLocation)
    eventHandlerActor ! hcdLocationChanged(hcdLocation)
  }

  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = s" validating command ----> ${controlCommand.commandName}")
    controlCommand.commandName.name match {

      case Commands.FOLLOW => {
        validateFollowCommand(controlCommand)
      }
      case Commands.MOVE => {
        validateMoveCommand(controlCommand)
      }
      case Commands.DATUM => {
        validateDatumCommand(controlCommand)
      }
      case Commands.DUMMY_IMMEDIATE => {
        executeDummyImmediateCommand(controlCommand)
      }
      case Commands.DUMMY_LONG => {
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.STARTUP => {
        CommandResponse.Accepted(controlCommand.runId)
      }
      case Commands.SHUTDOWN => {
        CommandResponse.Accepted(controlCommand.runId)
      }
      case x =>
        CommandResponse.Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }
  def executeDummyImmediateCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info(msg = s"Executing Dummy Immediate command  : ${controlCommand}")
    CommandResponse.Completed(controlCommand.runId)
  }
  /*
  This function validates follow command based on  assembly state received from MonitorActor
  if validation successful then returns command execution response else invalid command response
  is sent to caller

   */
  private def validateFollowCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating Follow Command")

    val assemblyCurrentState = getCurrentAssemblyState
    log.info(msg = s"Monitor Actor's current state while executing Follow command is  : ${assemblyCurrentState}")
    if (validateAssemblyState(assemblyCurrentState)) {
      executeFollowCommandAndSendResponse(controlCommand)
    } else {
      CommandResponse.NotAllowed(
        controlCommand.runId,
        UnsupportedCommandInStateIssue(s" Follow command is not allowed if assembly is not in Running state")
      )
    }
  }
  /*
    This function executes follow command by sending msg to commandhandler and sends response of commandhandler to
    the caller
   */
  private def executeFollowCommandAndSendResponse(controlCommand: ControlCommand): CommandResponse = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val immediateResponse: CommandMessage = Await.result(commandHandlerActor ? { ref: ActorRef[CommandMessage] =>
      CommandMessage.ImmediateCommand(ref, controlCommand)
    }, 10.seconds)
    immediateResponse match {
      case msg: ImmediateCommandResponse => {
        if (msg.commandResponse.toString.equals("Completed")) {
          monitorActor ! AssemblyOperationalStateChangeMsg(AssemblyOperationalState.Slewing)
        }
        msg.commandResponse
      }
      case _ => {
        CommandResponse.NotAllowed(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Follow command is not allowed if assembly is not in Running state")
        )
      }
    }
  }
  /*
    This function fetches current state of Monitor Actor using akka ask pattern
   */
  private def getCurrentAssemblyState(): MonitorMessage = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
      MonitorMessage.GetCurrentState(ref)
    }, 3.seconds)
  }
  /*
    This function checks whether assembly state is running or not
   */
  private def validateAssemblyState(assemblyCurrentState: MonitorMessage): Boolean = {
    assemblyCurrentState match {
      case x: MonitorMessage.AssemblyCurrentState => {
        log.info(msg = s"Assembly current state from monitor actor  is : ${x}")
        x.lifeCycleState.toString match {
          case "Running" if x.operationalState.toString().equals("Running") => {
            return true
          }
          case _ => {
            return false
          }
        }
      }
      case _ => {
        log.error(msg = s"Incorrect current state is provided to assembly by monitor actor")
        return false
      }
    }

  }
  /*
  This function checks whether aces parameters are provided or not
   */
  private def validateParams(controlCommand: ControlCommand): Boolean = {
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    log.info(s"axes value is ${axes}")
    val param = axes.head
    if (param == "BOTH" || param == "AZ" || param == "EL") {
      return true
    }
    return false
  }
  /*
    This function validates move command based on parameters and state
   */
  private def validateMoveCommand(controlCommand: ControlCommand): CommandResponse = {
    if (validateParams(controlCommand)) {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler
      val assemblyCurrentState = Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
        MonitorMessage.GetCurrentState(ref)
      }, 3.seconds)
      log.info(msg = s"Response from monitor actor is : ${assemblyCurrentState}")
      if (validateAssemblyState(assemblyCurrentState)) {
        monitorActor ! AssemblyOperationalStateChangeMsg(AssemblyOperationalState.Inposition)
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.NotAllowed(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Move command is not allowed if assembly is not in Running state")
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" axes parameter is not provided for move command"))
    }
  }
  /*
  This function validates datum command based on parameters and state
   */
  private def validateDatumCommand(controlCommand: ControlCommand): CommandResponse = {
    // check hcd is in running state
    if (validateParams(controlCommand)) {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler
      val assemblyCurrentState = Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
        MonitorMessage.GetCurrentState(ref)
      }, 3.seconds)
      log.info(msg = s"Response from monitor actor is : ${assemblyCurrentState}")
      if (validateAssemblyState(assemblyCurrentState)) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.NotAllowed(
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
