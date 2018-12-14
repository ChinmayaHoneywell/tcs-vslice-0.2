package org.tmt.tcs.mcs.MCShcd.msgTransformers
import java.time.Instant

import com.google.protobuf.GeneratedMessage
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.ControlCommand
import csw.params.core.generics.Parameter
import csw.params.core.states.CurrentState
import csw.params.events.SystemEvent
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsCommandProtos._
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsEventsProtos._

object ProtoBuffMsgTransformer {
  def create(loggerFactory: LoggerFactory): ProtoBuffMsgTransformer = ProtoBuffMsgTransformer(loggerFactory)
}

case class ProtoBuffMsgTransformer(loggerFactory: LoggerFactory) extends IMessageTransformer {
  private val log                                      = loggerFactory.getLogger
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)

  override def decodeCommandResponse(responsePacket: Array[Byte]): SubystemResponse = {

    //log.info(msg = s"Decoding command Response")

    val commandResponse: MCSCommandResponse   = MCSCommandResponse.parseFrom(responsePacket)
    val cmdError: MCSCommandResponse.CmdError = commandResponse.getCmdError
    //log.info(s"Command Response from Simulator is : ${commandResponse}")
    var cmdErrorBool: Boolean = false
    if (MCSCommandResponse.CmdError.OK.equals(cmdError)) {
      //log.info("No Error from Simulator")
      cmdErrorBool = true
      SubystemResponse(cmdErrorBool, None, None)
    } else {
      //log.error(s"Error from simulator cmdError : ${cmdError} and expected : ${MCSCommandResponse.CmdError.OK}")
      SubystemResponse(cmdErrorBool, Some(commandResponse.getCmdError.toString), Some(commandResponse.getErrorInfo))
    }

  }
  override def decodeEvent(eventName: String, encodedEventData: Array[Byte]): CurrentState = {
    eventName match {
      case EventConstants.CURRENT_POSITION =>
        val mcsCurrentPosEvent: McsCurrentPositionEvent = McsCurrentPositionEvent.parseFrom(encodedEventData)
        paramSetTransformer.getMountCurrentPosition(mcsCurrentPosEvent)
      case EventConstants.DIAGNOSIS_STATE =>
        val diagnosis: MountControlDiags = MountControlDiags.parseFrom(encodedEventData)
        paramSetTransformer.getMountControlDignosis(diagnosis)
      case EventConstants.DRIVE_STATE =>
        val driveState: McsDriveStatus = McsDriveStatus.parseFrom(encodedEventData)
        paramSetTransformer.getMCSDriveStatus(driveState)
      case EventConstants.HEALTH_STATE =>
        var healthState: McsHealth = null
        try {
          healthState = McsHealth.parseFrom(encodedEventData)
        } catch {
          case e: Exception =>
            e.printStackTrace()
            healthState = McsHealth
              .newBuilder()
              .setHealth(McsHealth.Health.Good)
              .setReason("All is well")
              .setTime(Instant.now().toEpochMilli)
              .build()
            log.error(s"Publishing dummy health from exception :$healthState")
        }
        paramSetTransformer.getMCSHealth(healthState)
    }

  }

  override def encodeMessage(controlCommand: ControlCommand): Array[Byte] = {
    //log.info(msg = s"Encoding command : ${controlCommand} with protobuff convertor")
    controlCommand.commandName.name match {
      case Commands.FOLLOW       => getFollowCommandBytes
      case Commands.DATUM        => getDatumCommandBytes(controlCommand)
      case Commands.POINT        => getPointCommandBytes(controlCommand)
      case Commands.POINT_DEMAND => getPointDemandCommandBytes(controlCommand)
      case Commands.STARTUP      => getStartupCommandBytes
      case Commands.SHUTDOWN     => getShutdownCommandBytes
    }
  }
  override def encodeCurrentState(currentState: CurrentState): Array[Byte] = {
    var azPos    = 0.0
    var elPos    = 0.0
    val sentTime = System.currentTimeMillis() //current time of HCD
    // log.info(s"Received current state for transformation to events : $currentState")
    /* if (currentState.exists(EventConstants.TrackIDKey)) {
      trackID = currentState.get(EventConstants.TrackIDKey).get.head
    }*/
    if (currentState.exists(EventConstants.AzPosKey)) {
      azPos = currentState.get(EventConstants.AzPosKey).get.head
    }
    if (currentState.exists(EventConstants.ElPosKey)) {
      elPos = currentState.get(EventConstants.ElPosKey).get.head
    }
    val event: GeneratedMessage =
      TcsPositionDemandEvent
        .newBuilder()
        .setAzimuth(azPos)
        .setElevation(elPos)
        .setHcdReceivalTime(currentState.get(EventConstants.HcdReceivalTime_Key).get.head)
        .setTpkPublishTime(currentState.get(EventConstants.TimeStampKey).get.head)
        .setAssemblyReceivalTime(currentState.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head)
        .build()
    // log.info(s"converted current state to ${event.toByteArray}")
    event.toByteArray
  }

  //TODO: Change this method for publishing assembly,hcd receival times
  override def encodeEvent(systemEvent: SystemEvent): Array[Byte] = {

    //   val trackIDOption: Option[Parameter[Int]] = systemEvent.get(EventConstants.TrackIDKey)

    var azParam = 0.0
    if (systemEvent.exists(EventConstants.AzPosKey)) {
      azParam = systemEvent.get(EventConstants.AzPosKey).get.head
    }
    var elParam = 0.0
    if (systemEvent.exists(EventConstants.ElPosKey)) {
      elParam = systemEvent.get(EventConstants.ElPosKey).get.head
    }

    // val trackID: Int = trackIDOption.get.head
    var assemblySentTime = System.currentTimeMillis()
    if (systemEvent.exists(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY)) {
      assemblySentTime = systemEvent.get(EventConstants.ASSEMBLY_RECEIVAL_TIME_KEY).get.head
    }

    val event = TcsPositionDemandEvent
      .newBuilder()
      .setAzimuth(azParam)
      .setElevation(elParam)
      .setHcdReceivalTime(systemEvent.get(EventConstants.HcdReceivalTime_Key).get.head)
      .setTpkPublishTime(systemEvent.get(EventConstants.TimeStampKey).get.head)
      .setAssemblyReceivalTime(assemblySentTime)
      .build()
    event.toByteArray

  }

  def getFollowCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = FollowCommand.newBuilder().build()
    command.toByteArray
  }
  def getDatumCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param1                  = axesParam.head
    var axes: Axes              = Axes.BOTH
    if (param1 == "AZ") {
      axes = Axes.AZ
    }
    if (param1 == "EL") {
      axes = Axes.EL
    }

    val command: GeneratedMessage = DatumCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param1                  = axesParam.head
    var axes: Axes              = Axes.BOTH
    if (param1 == "AZ") {
      axes = Axes.AZ
    }
    if (param1 == "EL") {
      axes = Axes.EL
    }

    val command: GeneratedMessage = PointCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointDemandCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val azParam: Parameter[_]     = controlCommand.paramSet.find(msg => msg.keyName == "AZ").get
    val azValue: Any              = azParam.head
    val elParam: Parameter[_]     = controlCommand.paramSet.find(msg => msg.keyName == "EL").get
    val elValue: Any              = elParam.head
    val az: Double                = azValue.asInstanceOf[Number].doubleValue()
    val el: Double                = elValue.asInstanceOf[Number].doubleValue()
    val command: GeneratedMessage = PointDemandCommand.newBuilder().setAZ(az).setEL(el).build()
    command.toByteArray
  }
  def getStartupCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = Startup.newBuilder().build()
    command.toByteArray
  }
  def getShutdownCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = Shutdown.newBuilder().build()
    command.toByteArray
  }

}
