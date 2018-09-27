package org.tmt.tcs.mcs.MCShcd.msgTransformers

import java.time.Instant

import csw.messages.commands.CommandIssue.WrongInternalStateIssue
import csw.messages.commands.{CommandIssue, CommandResponse}
import csw.messages.events.SystemEvent
import csw.messages.params.generics.{Key, Parameter}
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.messages.params.models.Units.degree
import csw.messages.params.states.{CurrentState, StateName}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsEventsProtos.{
  McsCurrentPositionEvent,
  McsDriveStatus,
  McsHealth,
  MountControlDiags
}

object ParamSetTransformer {
  def create(loggerFactory: LoggerFactory): ParamSetTransformer = ParamSetTransformer(loggerFactory)
}

case class ParamSetTransformer(loggerFactory: LoggerFactory) {

  private val prefix                     = Prefix(Subsystem.MCS.toString)
  private val timeStampKey: Key[Instant] = EventConstants.TimeStampKey
  def getMountDemandPositions(paramSet: Set[Parameter[_]]): MCSPositionDemand = {
    val azPosParam: Parameter[_]   = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS).get
    val elPosParam: Parameter[_]   = paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS).get
    val trackIDParam: Parameter[_] = paramSet.find(msg => msg.keyName == EventConstants.POITNTING_KERNEL_TRACK_ID).get
    val az: Double                 = azPosParam.head.asInstanceOf[Number].doubleValue()
    val el: Double                 = elPosParam.head.asInstanceOf[Number].doubleValue()
    val trackID: Int               = trackIDParam.head.asInstanceOf[Integer].intValue()
    MCSPositionDemand(trackID, az, el)
  }
  def getMountDemandPositions(systemEvent: SystemEvent): MCSPositionDemand = {
    val azParamOption: Option[Parameter[Double]] = systemEvent.get(EventConstants.AzPosKey)
    val elParamOption: Option[Parameter[Double]] = systemEvent.get(EventConstants.ElPosKey)
    val trackIDOption: Option[Parameter[Int]]    = systemEvent.get(EventConstants.TrackIDKey)

    val azParam: Double = azParamOption.get.head
    val elParam: Double = elParamOption.get.head
    val trackID: Int    = trackIDOption.get.head
    MCSPositionDemand(trackID, azParam, elParam)
  }
  def getHCDState(state: String): CurrentState = {
    val lifeCycleStateKey                 = EventConstants.LifeCycleStateKey
    val lifeCycleParam: Parameter[String] = lifeCycleStateKey.set(state)

    val timestamp = timeStampKey.set(Instant.now)

    CurrentState(prefix, StateName(EventConstants.HCDLifecycleState)).add(lifeCycleParam).add(timestamp)
  }
  /*
    This function takes mcs current position proto as input and transforms it into
    CSW current state for this it uses keys present EventConstants Helper class.
   */
  def getMountCurrentPosition(mcsCurrentPosEvent: McsCurrentPositionEvent): CurrentState = {

    val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(mcsCurrentPosEvent.getAzPos).withUnits(degree)
    val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(mcsCurrentPosEvent.getElPos).withUnits(degree)

    val azPosErrorParam: Parameter[Double] =
      EventConstants.AZ_POS_ERROR_KEY.set(mcsCurrentPosEvent.getAzPosError).withUnits(degree)
    val elPosErrorParam: Parameter[Double] =
      EventConstants.EL_POS_ERROR_KEY.set(mcsCurrentPosEvent.getElPosError).withUnits(degree)

    val azInPositionParam: Parameter[Boolean] = EventConstants.AZ_InPosition_Key.set(mcsCurrentPosEvent.getAzInPosition)
    val elInPositionParam: Parameter[Boolean] = EventConstants.EL_InPosition_Key.set(mcsCurrentPosEvent.getElInPosition)
    val timestamp                             = timeStampKey.set(Instant.ofEpochMilli(mcsCurrentPosEvent.getTime))

    CurrentState(prefix, StateName(EventConstants.CURRENT_POSITION))
      .add(azPosParam)
      .add(elPosParam)
      .add(azPosErrorParam)
      .add(elPosErrorParam)
      .add(azInPositionParam)
      .add(elInPositionParam)
      .add(timestamp)
  }
  /*
    This function takes MountControlDiags Proto as input and transforms it into
    CSW CurrentState object for publishing to Assembly
   */
  def getMountControlDignosis(diagnosis: MountControlDiags): CurrentState = {
    val azPosParam: Parameter[Double] = EventConstants.AzPosKey.set(diagnosis.getAzPosDemand).withUnits(degree)
    val elPosParam: Parameter[Double] = EventConstants.ElPosKey.set(diagnosis.getElPosDemand).withUnits(degree)

    val azPosErrorParam: Parameter[Double] = EventConstants.AZ_POS_ERROR_KEY.set(diagnosis.getAzPosError).withUnits(degree)
    val elPosErrorParam: Parameter[Double] = EventConstants.EL_POS_ERROR_KEY.set(diagnosis.getElPosError).withUnits(degree)

    val azInPositionParam: Parameter[Boolean] = EventConstants.AZ_InPosition_Key.set(diagnosis.getAzInPosition)
    val elInPositionParam: Parameter[Boolean] = EventConstants.EL_InPosition_Key.set(diagnosis.getElInPosition)
    val timestamp                             = timeStampKey.set(Instant.ofEpochMilli(diagnosis.getTime))

    CurrentState(prefix, StateName(EventConstants.DIAGNOSIS_STATE))
      .add(azPosParam)
      .add(elPosParam)
      .add(azPosErrorParam)
      .add(elPosErrorParam)
      .add(azInPositionParam)
      .add(elInPositionParam)
      .add(timestamp)

  }
  /*
  This function takes MCSDriveStatus proto as input and returns CSW currentState
  populated with drive status parameters
   */
  def getMCSDriveStatus(driveStatus: McsDriveStatus): CurrentState = {
    val processingCmdParam: Parameter[Boolean]    = EventConstants.PROCESSING_PARAM_KEY.set(driveStatus.getProcessing)
    val mcdLifecycleStateParam: Parameter[String] = EventConstants.LifeCycleStateKey.set(driveStatus.getLifecycle.toString)
    val mcsAzState: Parameter[String]             = EventConstants.MCS_AZ_STATE.set(driveStatus.getAzstate.name())
    val mcsElState: Parameter[String]             = EventConstants.MCS_EL_STATE.set(driveStatus.getElstate.name())
    val timestamp                                 = timeStampKey.set(Instant.ofEpochMilli(driveStatus.getTime))

    CurrentState(prefix, StateName(EventConstants.DRIVE_STATE))
      .add(processingCmdParam)
      .add(mcdLifecycleStateParam)
      .add(mcsAzState)
      .add(mcsElState)
      .add(timestamp)
  }
  def getMCSHealth(health: McsHealth): CurrentState = {
    val healthParam: Parameter[String]       = EventConstants.HEALTH_KEY.set(health.getHealth.name())
    val healthReasonParam: Parameter[String] = EventConstants.HEALTH_REASON_KEY.set(health.getReason)
    val timestamp                            = timeStampKey.set(Instant.ofEpochMilli(health.getTime))

    CurrentState(prefix, StateName(EventConstants.HEALTH_STATE))
      .add(healthParam)
      .add(healthReasonParam)
      .add(timestamp)
  }

  def getCSWResponse(runID: Id, subsystemResponse: SubystemResponse): CommandResponse = {
    if (subsystemResponse.commandResponse) {
      CommandResponse.Completed(runID)
    } else {
      decodeErrorState(runID, subsystemResponse)
    }

  }
  def decodeErrorState(runID: Id, response: SubystemResponse): CommandResponse = {
    response.errorReason.get match {
      case "ILLEGAL_STATE" => {
        return CommandResponse.Invalid(runID, WrongInternalStateIssue(response.errorInfo.get))
      }
      case "BUSY" => {
        return CommandResponse.NotAllowed(runID, CommandIssue.OtherIssue(response.errorInfo.get))
      }
      case "OUT_OF_RANGE" => {
        return CommandResponse.Invalid(runID, CommandIssue.ParameterValueOutOfRangeIssue(response.errorInfo.get))
      }
      case "OUT_OF_SPEC" => {
        return CommandResponse.Invalid(runID, CommandIssue.WrongParameterTypeIssue(response.errorInfo.get))
      }
      case "FAILED" => {
        return CommandResponse.Error(runID, response.errorInfo.get)
      }
      case _ => return CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
    }
    CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
  }

}
