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

  /*
  This function takes assemblyCurrentState as input and returns
  AssemblyState system event
   */
  def getAssemblyEvent(assemblyState: AssemblyCurrentState): Event = {
    val lifeCycleKey        = EventHandlerConstants.LifecycleStateKey
    val operationalStateKey = EventHandlerConstants.OperationalStateKey

    val lifecycleParam: Parameter[String]   = lifeCycleKey.set(assemblyState.lifeCycleState.toString)
    val operationalParam: Parameter[String] = operationalStateKey.set(assemblyState.operationalState.toString)

    SystemEvent(EventHandlerConstants.assemblyStateEventPrefix,
                EventHandlerConstants.eventName,
                Set(lifecycleParam, operationalParam))
  }

  /*
    This function converts currentPosition from HCD wrapped in  currentState to systemEvent
   */
  def getCurrentPositionEvent(currentState : CurrentState) : Event ={
     val azPosParam : Option[Parameter[Double]] = currentState.get(EventHandlerConstants.AzPosKey)
     val elPosParam : Option[Parameter[Double]] = currentState.get(EventHandlerConstants.ElPosKey)
     val azPosErrorParam : Option[Parameter[Double]] = currentState.get(EventHandlerConstants.AZ_POS_ERROR_KEY)
     val elPosErrorParam : Option[Parameter[Double]] = currentState.get(EventHandlerConstants.EL_POS_ERROR_KEY)
     val azInPosKey : Option[Parameter[Boolean]] = currentState.get(EventHandlerConstants.AZ_InPosition_Key)
     val elInPosKey : Option[Parameter[Boolean]] = currentState.get(EventHandlerConstants.EL_InPosition_Key)
     val timeStampKey : Option[Parameter[Instant]] = currentState.get(EventHandlerConstants.TimeStampKey)

     SystemEvent(EventHandlerConstants.CURRENT_POSITION_PREFIX,EventHandlerConstants.CURRENT_POSITION_STATE,
       Set(azPosParam.get,elPosParam.get,azPosErrorParam.get,elPosErrorParam.get,azInPosKey.get,elInPosKey.get,timeStampKey.get))
  }
  def getDiagnosisEvent(currentState : CurrentState) : Event = {
    SystemEvent(EventHandlerConstants.DIAGNOSIS_PREFIX,EventHandlerConstants.DIAGNOSIS_STATE,
      Set())
  }
  def getHealthEvent(currentState : CurrentState) : Event = {

  }
  def getDriveState(currentState : CurrentState) : Event ={

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

}
