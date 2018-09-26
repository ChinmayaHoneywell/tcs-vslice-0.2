package org.tmt.tcs.mcs.MCSassembly.msgTransformer

import java.time.Instant

import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.events.{Event, SystemEvent}
import csw.messages.params.generics.Parameter
import csw.messages.params.states.CurrentState
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.{Commands, EventConstants, EventHandlerConstants}
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage.AssemblyCurrentState

object EventTransformerHelper {
  def create(loggerFactory: LoggerFactory): EventTransformerHelper = EventTransformerHelper(loggerFactory)
}
case class EventTransformerHelper(loggerFactory: LoggerFactory) {

  private val log = loggerFactory.getLogger

  //This parameter is needed for dummyEvent
  var i: Int = 10

  /*
  This function takes assemblyCurrentState as input and returns
  AssemblyState system event
   */
  def getAssemblyEvent(assemblyState: AssemblyCurrentState): Event = {

    log.info("Transforming state : ${assemblyState}  to systemEvent")

    val lifeCycleKey        = EventHandlerConstants.LifecycleStateKey
    val operationalStateKey = EventHandlerConstants.OperationalStateKey

    val lifecycleParam: Parameter[String]   = lifeCycleKey.set(assemblyState.lifeCycleState.toString)
    val operationalParam: Parameter[String] = operationalStateKey.set(assemblyState.operationalState.toString)

    val systemEvent = SystemEvent(EventHandlerConstants.assemblyStateEventPrefix,
                                  EventHandlerConstants.eventName,
                                  Set(lifecycleParam, operationalParam))
    //log.info(s"Transformed assemblyState is : ${systemEvent}")
    systemEvent

  }

  /*
    This function converts currentPosition from HCD wrapped in  currentState to systemEvent
   */
  def getCurrentPositionEvent(currentState: CurrentState): Event = {
    val azPosParam: Option[Parameter[Double]]      = currentState.get(EventHandlerConstants.AzPosKey)
    val elPosParam: Option[Parameter[Double]]      = currentState.get(EventHandlerConstants.ElPosKey)
    val azPosErrorParam: Option[Parameter[Double]] = currentState.get(EventHandlerConstants.AZ_POS_ERROR_KEY)
    val elPosErrorParam: Option[Parameter[Double]] = currentState.get(EventHandlerConstants.EL_POS_ERROR_KEY)
    val azInPosKey: Option[Parameter[Boolean]]     = currentState.get(EventHandlerConstants.AZ_InPosition_Key)
    val elInPosKey: Option[Parameter[Boolean]]     = currentState.get(EventHandlerConstants.EL_InPosition_Key)
    val timeStampKey: Option[Parameter[Instant]]   = currentState.get(EventHandlerConstants.TimeStampKey)

    SystemEvent(
      EventHandlerConstants.CURRENT_POSITION_PREFIX,
      EventHandlerConstants.CURRENT_POSITION_STATE,
      Set(azPosParam.get,
          elPosParam.get,
          azPosErrorParam.get,
          elPosErrorParam.get,
          azInPosKey.get,
          elInPosKey.get,
          timeStampKey.get)
    )
  }
  def getDiagnosisEvent(currentState: CurrentState): Event = {
    val azPosParam: Option[Parameter[Double]]      = currentState.get(EventHandlerConstants.AzPosKey)
    val elPosParam: Option[Parameter[Double]]      = currentState.get(EventHandlerConstants.ElPosKey)
    val azPosErrorParam: Option[Parameter[Double]] = currentState.get(EventHandlerConstants.AZ_POS_ERROR_KEY)
    val elPosErrorParam: Option[Parameter[Double]] = currentState.get(EventHandlerConstants.EL_POS_ERROR_KEY)
    val azInPosKey: Option[Parameter[Boolean]]     = currentState.get(EventHandlerConstants.AZ_InPosition_Key)
    val elInPosKey: Option[Parameter[Boolean]]     = currentState.get(EventHandlerConstants.EL_InPosition_Key)
    val timeStampKey: Option[Parameter[Instant]]   = currentState.get(EventHandlerConstants.TimeStampKey)

    SystemEvent(
      EventHandlerConstants.DIAGNOSIS_PREFIX,
      EventHandlerConstants.DIAGNOSIS_STATE,
      Set(azPosParam.get,
          elPosParam.get,
          azPosErrorParam.get,
          elPosErrorParam.get,
          azInPosKey.get,
          elInPosKey.get,
          timeStampKey.get)
    )
  }
  def getHealthEvent(currentState: CurrentState): Event = {
    val health: Option[Parameter[String]]        = currentState.get(EventHandlerConstants.HEALTH_KEY)
    val healthReason: Option[Parameter[String]]  = currentState.get(EventHandlerConstants.HEALTH_REASON_KEY)
    val timeStampKey: Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.TimeStampKey)

    SystemEvent(EventHandlerConstants.HEALTH_PREFIX,
                EventHandlerConstants.HEALTH_STATE,
                Set(health.get, healthReason.get, timeStampKey.get))
  }
  def getDriveState(currentState: CurrentState): Event = {
    val processing: Option[Parameter[Boolean]]    = currentState.get(EventHandlerConstants.PROCESSING_PARAM_KEY)
    val lifecycleState: Option[Parameter[String]] = currentState.get(EventHandlerConstants.MCS_LIFECYCLE_STATTE_KEY)
    val azState: Option[Parameter[String]]        = currentState.get(EventHandlerConstants.MCS_AZ_STATE)
    val elState: Option[Parameter[String]]        = currentState.get(EventHandlerConstants.MCS_EL_STATE)
    val timeStampKey: Option[Parameter[Instant]]  = currentState.get(EventHandlerConstants.TimeStampKey)

    SystemEvent(
      EventHandlerConstants.HEALTH_PREFIX,
      EventHandlerConstants.HEALTH_STATE,
      Set(processing.get, lifecycleState.get, azState.get, elState.get, timeStampKey.get)
    )
  }

  /*
  This function takes system event as input and from systemEvent it builds  controlCommand object
  for sending to HCD as oneWayCommand
   */
  def getOneWayCommandObject(systemEvent: SystemEvent): ControlCommand = {
    val azParam: Option[Parameter[Double]]       = systemEvent.get(EventHandlerConstants.AzPosKey)
    val elParamOption: Option[Parameter[Double]] = systemEvent.get(EventHandlerConstants.ElPosKey)
    val trackIDOption: Option[Parameter[Int]]    = systemEvent.get(EventHandlerConstants.TrackIDKey)

    val setup = Setup(EventHandlerConstants.mcsHCDPrefix, CommandName(Commands.POSITION_DEMANDS), None)
    setup.add(azParam.get).add(elParamOption.get).add(trackIDOption.get)
    setup

  }
  /*
    This is dummy event which assembly publishes every 10 seconds
    It has only one parameter int i which is incremented every time this event
    we publish.
   */
  def getDummyAssemblyEvent(): SystemEvent = {

    val dummyEventKey = EventHandlerConstants.DummyEventKey
    i = i + 1
    val intParam: Parameter[Int] = dummyEventKey.set(i)

    SystemEvent(EventHandlerConstants.DUMMY_STATE_PREFIX, EventHandlerConstants.DUMMY_STATE).add(intParam)
  }

}
