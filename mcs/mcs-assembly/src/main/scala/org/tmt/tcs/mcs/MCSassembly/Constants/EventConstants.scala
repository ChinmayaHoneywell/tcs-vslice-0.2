package org.tmt.tcs.mcs.MCSassembly.Constants

object EventConstants {
  val MOUNT_DEMAND_POSITION     = "MountDemandPosition"
  val TPK_PREFIX                = "TCS.PK.PKA"
  val POITNTING_KERNEL_TRACK_ID = "trackID"
  val POINTING_KERNEL_AZ_POS    = "az_pos"
  val POINTING_KERNEL_EL_POS    = "el_pos"
  val LIFECYLE_STATE_KEY        = "LifecycleState"
  val OPERATIONAL_STATE_KEY     = "OperationalState"
  val ASSEMBLY_STATE_EVENT      = "AssemblyState"

  //HCDLifecycleState currentState constants
  val HCDLifecycleState: String = "HCDLifecycleState"
  val HCDState_Off: String      = "Off"
  val HCDState_Running: String  = "Running"
  val HCDState_Initialized      = "Initialized"
  val AZ_InPosition: String = "azInPositionKey"
  val EL_InPosition: String = "elInPositionKey"
  val AZ_POS_ERROR: String  = "azPosErrorKey"
  val EL_POS_ERROR: String  = "elPosErrorKey"
  val TIMESTAMP :  String = "timeStamp"

  //MCS Simulator currentPosition state
  val CURRENT_POSITION: String = "CurrentPosition"

  //MCS Diagnosis event
  val DIAGNOSIS_STATE: String = "Diagnosis"

  // MCS Health event
  val HEALTH_STATE: String = "Health"

  //MCS Drive Status Event
  val DRIVE_STATE: String = "DriveStatus"

}
