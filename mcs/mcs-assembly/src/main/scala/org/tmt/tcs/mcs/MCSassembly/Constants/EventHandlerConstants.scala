package org.tmt.tcs.mcs.MCSassembly.Constants

import java.time.Instant

import csw.messages.events.{EventKey, EventName}
import csw.messages.params.generics.{Key, KeyType, Parameter}
import csw.messages.params.models.Units.degree
import csw.messages.params.models.{Prefix, Subsystem}
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants

object EventHandlerConstants {
  //These parameters are needed by Position Demand Event
  val PositionDemandKey: Set[EventKey] = Set(
    EventKey(Prefix(EventConstants.TPK_PREFIX), EventName(EventConstants.MOUNT_DEMAND_POSITION))
  )
  val TrackIDKey: Key[Int]  = KeyType.IntKey.make(EventConstants.POITNTING_KERNEL_TRACK_ID)
  val AzPosKey: Key[Double] = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double] = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_EL_POS)
  val mcsHCDPrefix          = Prefix(Subsystem.MCS.toString)

  //These parameters are needed for assembly current state event generation
  val LifecycleStateKey: Key[String]   = KeyType.StringKey.make(EventConstants.LIFECYLE_STATE_KEY)
  val OperationalStateKey: Key[String] = KeyType.StringKey.make(EventConstants.OPERATIONAL_STATE_KEY)
  val assemblyStateEventPrefix         = Prefix("tmt.tcs.mcs.state")
  val eventName                        = EventName(EventConstants.ASSEMBLY_STATE_EVENT)

  //These parameters are needed for current position event send from assembly to tpk
  val CURRENT_POSITION_STATE = EventName(EventConstants.CURRENT_POSITION)
  val CURRENT_POSITION_PREFIX = Prefix("tmt.tcs.mcs.currentposition")
  val AzPosKey: Key[Double] = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_AZ_POS)
  val ElPosKey: Key[Double] = KeyType.DoubleKey.make(EventConstants.POINTING_KERNEL_EL_POS)
  val AZ_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(EventConstants.AZ_POS_ERROR)
  val EL_POS_ERROR_KEY: Key[Double] = KeyType.DoubleKey.make(EventConstants.EL_POS_ERROR)
  val AZ_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EventConstants.AZ_InPosition)
  val EL_InPosition_Key: Key[Boolean] = KeyType.BooleanKey.make(EventConstants.EL_InPosition)
  val TimeStampKey: Key[Instant] = KeyType.TimestampKey.make(EventConstants.TIMESTAMP)


  //These parameters are needed for diagnosis event
  val DIAGNOSIS_STATE = EventName(EventConstants.DIAGNOSIS_STATE)
  val DIAGNOSIS_PREFIX = Prefix("tmt.tcs.mcs.diagnostics")


}
