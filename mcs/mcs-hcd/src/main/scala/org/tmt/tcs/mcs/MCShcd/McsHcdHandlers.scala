package org.tmt.tcs.mcs.MCShcd

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import csw.framework.scaladsl.ComponentHandlers
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.commands.CommandIssue.{UnsupportedCommandInStateIssue, UnsupportedCommandIssue, WrongInternalStateIssue, WrongNumberOfParametersIssue}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.generics.Parameter
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.EventMessage.{HCDOperationalStateChangeMsg, StartEventSubscription, StateChangeMsg}
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.ShutdownMsg
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import akka.actor.typed.scaladsl.AskPattern._
import com.typesafe.config.Config
import csw.framework.CurrentStatePublisher
import csw.messages.TopLevelActorMessage
import csw.services.command.CommandResponseManager
import csw.services.event.api.scaladsl.EventService
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.ImmediateCommandResponse
import org.tmt.tcs.mcs.MCShcd.Protocol.{ZeroMQMessage, ZeroMQProtocolActor}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.ParamSetTransformer
import org.tmt.tcs.mcs.MCShcd.workers.PositionDemandActor

import scala.concurrent.Await
import scala.concurrent.duration._
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

 // private val simulator: SimpleSimulator = SimpleSimulator.create(loggerFactory)
  private val statePublisherActor: ActorRef[EventMessage] = ctx.spawn(
    StatePublisherActor.createObject(currentStatePublisher,
                                     HCDLifeCycleState.Off,
                                     HCDOperationalState.DrivePowerOff,
                                     eventService,
                                     loggerFactory),
    "StatePublisherActor"
  )
  //private val subSystemManager: SubsystemManager = SubsystemManager.create(simulator, loggerFactory)
  private val zeroMQProtoActor: ActorRef[ZeroMQMessage] =
    ctx.spawn(ZeroMQProtocolActor.create(statePublisherActor, loggerFactory), "ZeroMQActor")
  private val commandHandlerActor: ActorRef[HCDCommandMessage] =
    ctx.spawn(CommandHandlerActor.createObject(commandResponseManager, lifeCycleActor, zeroMQProtoActor, loggerFactory),
              "CommandHandlerActor")
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  private val positionDemandActor: ActorRef[ControlCommand] =
    ctx.spawn(PositionDemandActor.create(loggerFactory,zeroMQProtoActor,paramSetTransformer), "PositionDemandEventActor")
  /*
  This function initializes HCD, uses configuration object to initialize Protocol and
  sends updated states tp state publisher actor for publishing
   */
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS HCD")
    //Todo : Temporarily commenting call to config and lifecycle actor
    /*  implicit val duration: Timeout = 45 seconds
    implicit val scheduler         = ctx.system.scheduler

    val lifecycleMsg = Await.result(lifeCycleActor ? { ref: ActorRef[LifeCycleMessage] =>
      LifeCycleMessage.InitializeMsg(ref)
    }, 40.seconds)*/

    var config: Config = null
    /*  lifecycleMsg match {
      case x: LifeCycleMessage.HCDConfig => {
        config = x.config
      }
    }*/
    // subSystemManager.initialize(config)

    statePublisherActor ! StartEventSubscription(zeroMQProtoActor)
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
      case Commands.POSITION_DEMANDS => {
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

  private def getHCDCurrentState(): EventMessage = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler

    Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
      EventMessage.GetCurrentState(ref)
    }, 3.seconds)
  }
  /*
       This functions validates follow  command based upon parameters and hcd state
       It has 2 internal functions 1 is for validating parameterSet and 1 is for
       validating HCDCurrentState.
       If both functions returns positive response then only it processes command else rejects
       command execution.
       If validation function response is successful then it executes follow command and sends follow commamnd
       execution response to the caller.

   */
  private def validateFollowCommand(controlCommand: ControlCommand): CommandResponse = {
    log.info("Validating follow command in HCD")
    def validateParamset: Boolean = {
      return controlCommand.paramSet.isEmpty
    }
    def validateHCDState: Boolean = {
      val hcdCurrentState = getHCDCurrentState()
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
        executeFollowCommandAndSendResponse(controlCommand)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem must be in ${HCDLifeCycleState.Running} state" +
            s"and operational state must be in ${HCDOperationalState.ServoOffDatumed} to process follow command"
          )
        )
      }
    }
    CommandResponse.Invalid(controlCommand.runId, WrongNumberOfParametersIssue("Follow command should not have any parameters"))
  }

  /*
  This function executes follow command and sends follow command execution
  response to caller, if follow command execution response is successful
  then it changes state of statePublisherActor to Following

   */
  private def executeFollowCommandAndSendResponse(controlCommand: ControlCommand): CommandResponse = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    val immediateResponse: HCDCommandMessage = Await.result(commandHandlerActor ? { ref: ActorRef[HCDCommandMessage] =>
      HCDCommandMessage.ImmediateCommand(ref, controlCommand)
    }, 10.seconds)
    immediateResponse match {
      case msg: ImmediateCommandResponse => {
        if (msg.commandResponse.toString.equals("Completed")) {
          statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.Following)
          //statePublisherActor ! publishCurrentPosition()
        }
        msg.commandResponse
      }
      case _ => {
        CommandResponse.NotAllowed(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Follow command is not allowed if HCD is not in Running state")
        )
      }
    }
  }
  /*
       This functions routes all commands to commandhandler actor and bsed upon command execution updates states of HCD by sending it
       to StatePublisher actor
   */
  override def onSubmit(controlCommand: ControlCommand): Unit = {

    commandHandlerActor ! HCDCommandMessage.submitCommand(controlCommand)
    controlCommand.commandName.name match {
      case Commands.STARTUP => {
        log.info("On receipt of startup command changing MCS HCD state to Running")

        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Running, HCDOperationalState.DrivePowerOff)
      }
      case Commands.SHUTDOWN => {
        log.info("On receipt of shutdown command changing MCS HCD state to Disconnected")

        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Off, HCDOperationalState.Disconnected)
      }
      case Commands.DATUM => {
        log.info("changing HCD's operational state to ServoOffDatumed")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.ServoOffDatumed)
      }
      case Commands.POINT | Commands.POINT_DEMAND => {
        log.info("changing HCD's operational state to pointing")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.PointingDatumed)
        //statePublisherActor ! publishCurrentPosition()
      }
    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.info(msg = "Sending position demands to MCS Simulator in HCDHandler oneway loop")
    positionDemandActor ! controlCommand
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
