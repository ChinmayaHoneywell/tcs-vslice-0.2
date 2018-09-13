package org.tmt.tcs.mcs.MCShcd.constants

import java.time.Instant

import csw.messages.events.{EventKey, EventName}
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.Prefix

object EventConstants {
  val MOUNT_DEMAND_POSITION: String     = "MountDemandPosition"
  val TPK_PREFIX: String                = "TCS.PK.PKA"
  val POITNTING_KERNEL_TRACK_ID: String = "trackID"
  val POINTING_KERNEL_AZ_POS: String    = "az_pos"
  val POINTING_KERNEL_EL_POS: String    = "el_pos"

  val PositionDemandKey: Set[EventKey] = Set(EventKey(Prefix(TPK_PREFIX), EventName(MOUNT_DEMAND_POSITION)))

  val TrackIDKey: Key[Int]  = KeyType.IntKey.make(POITNTING_KERNEL_TRACK_ID)
  val AzPosKey: Key[Double] = KeyType.DoubleKey.make(POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double] = KeyType.DoubleKey.make(POINTING_KERNEL_EL_POS)

  //for MCS Simulator current position
  val AZ_POS_ERROR: String  = "azPosErrorKey"
  val EL_POS_ERROR: String  = "elPosErrorKey"
  val AZ_InPosition: String = "azInPositionKey"
  val EL_InPosition: String = "elInPositionKey"

  val AZ_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(EL_POS_ERROR)

  val AZ_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EL_InPosition)

  // used by all currentStates published by StatePublisherActor
  val TIMESTAMP: String          = "timeStamp"
  val TimeStampKey: Key[Instant] = KeyType.TimestampKey.make(TIMESTAMP)

  //LifecycleState currentState
  val HCDLifecycleState: String = "HCDLifecycleState"
  val LifeCycleStateKey         = KeyType.StringKey.make(HCDLifecycleState)

  //MCS Simulator currentPosition state
  val CURRENT_POSITION: String = "CurrentPosition"

  //MCS Diagnosis event
  val DIAGNOSIS_STATE: String = "Diagnosis"

  // MCS Health event
  val HEALTH_STATE: String           = "Health"
  val HEALTH_KEY: Key[String]        = KeyType.StringKey.make("HealthKey")
  val HEALTH_REASON_KEY: Key[String] = KeyType.StringKey.make("HealthReasonKey")

  //MCS Drive Status Event
  val DRIVE_STATE: String                  = "DriveStatus"
  val PROCESSING_PARAM_KEY: Key[Boolean]   = KeyType.BooleanKey.make("processingCommand")
  val MCS_LIFECYCLE_STATE_KEY: Key[String] = KeyType.StringKey.make("mcsLifecycleState")
  val MCS_AZ_STATE: Key[String]            = KeyType.StringKey.make("mcsAZState")
  val MCS_EL_STATE: Key[String]            = KeyType.StringKey.make("mcsELState")

}
