package org.tmt.tcs.mcs.MCSdeploy.constants

import java.time.Instant

import csw.messages.events.{EventKey, EventName}
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.{Prefix, Subsystem}
import org.tmt.tcs.mcs.MCSdeploy.constants.EventConstants.{
  DIAGNOSIS_PREFIX,
  DIAGNOSIS_STATE,
  DRIVE_STATE,
  DUMMY_STATE,
  DUMMY_STATE_PREFIX,
  HEALTH_STATE,
  MCS_AZ_STATE,
  MCS_EL_STATE,
  _
}

object DeployConstants {
  //Below are set of keys required for subscribing to events
  val PositionDemandKey: Set[EventKey] = Set(
    EventKey(Prefix(TPK_PREFIX), EventName(MOUNT_DEMAND_POSITION))
  )
  val currentPositionSet: Set[EventKey] = Set(EventKey(CURRENT_POSITION_PREFIX, CURRENT_POSITION_STATE))

  val mcsHCDPrefix = Prefix(Subsystem.MCS.toString)

  //These parameters are needed for assembly current state event generation
  val LifecycleStateKey: Key[String]   = KeyType.StringKey.make(LIFECYLE_STATE_KEY)
  val OperationalStateKey: Key[String] = KeyType.StringKey.make(OPERATIONAL_STATE_KEY)
  val assemblyStateEventPrefix         = Prefix(lIFECYLE_STATE_PREFIX)
  val eventName                        = EventName(ASSEMBLY_STATE_EVENT)

  //This parameter is needed for position demand event sent from Assemby to HCD
  val TrackIDKey: Key[Int] = KeyType.IntKey.make(POITNTING_KERNEL_TRACK_ID)

  //These parameters are needed for current position event send from assembly to tpk
  val CURRENT_POSITION_STATE          = EventName(CURRENT_POSITION)
  val CURRENT_POSITION_PREFIX         = Prefix(CURRENT_POS_PREFIX)
  val AzPosKey: Key[Double]           = KeyType.DoubleKey.make(POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double]           = KeyType.DoubleKey.make(POINTING_KERNEL_EL_POS)
  val AZ_POS_ERROR_KEY: Key[Double]   = KeyType.DoubleKey.make(AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double]   = KeyType.DoubleKey.make(EL_POS_ERROR)
  val AZ_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EL_InPosition)
  val TimeStampKey: Key[Instant]      = KeyType.TimestampKey.make(TIMESTAMP)

  //These parameters are needed for diagnosis event
  val DIAGNOSIS_STATE  = EventName(EventConstants.DIAGNOSIS_STATE)
  val DIAGNOSIS_PREFIX = Prefix(EventConstants.DIAGNOSIS_PREFIX)

  //These parameters are needed for Health event
  val HEALTH_STATE                   = EventName(EventConstants.HEALTH_STATE)
  val HEALTH_PREFIX                  = Prefix(EventConstants.Health_Prefix)
  val HEALTH_KEY: Key[String]        = KeyType.StringKey.make(EventConstants.HealthKey)
  val HEALTH_REASON_KEY: Key[String] = KeyType.StringKey.make(EventConstants.HealthReason)

  //These parameters are needed for Drive State
  val DRIVE_STATE                           = EventName(EventConstants.DRIVE_STATE)
  val LIFECYCLE_PREFIX                      = Prefix(EventConstants.DRIVE_STATE_PREFIX)
  val PROCESSING_PARAM_KEY: Key[Boolean]    = KeyType.BooleanKey.make(EventConstants.MCS_PROCESSING_COMMAND)
  val MCS_LIFECYCLE_STATTE_KEY: Key[String] = KeyType.StringKey.make(EventConstants.MCS_LIFECYCLE_STATE_KEY)
  val MCS_AZ_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_AZ_STATE)
  val MCS_EL_STATE: Key[String]             = KeyType.StringKey.make(EventConstants.MCS_EL_STATE)

  //These are parameters needed for dummy events
  val DUMMY_STATE             = EventName(EventConstants.DUMMY_STATE)
  val DUMMY_STATE_PREFIX      = Prefix(EventConstants.DUMMY_STATE_PREFIX)
  val DummyEventKey: Key[Int] = KeyType.IntKey.make(DUMMY_STATE_KEY)
}
