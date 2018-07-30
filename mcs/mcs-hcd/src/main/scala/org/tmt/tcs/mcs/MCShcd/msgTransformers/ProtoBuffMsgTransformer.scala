package org.tmt.tcs.mcs.MCShcd.msgTransformers
import com.google.protobuf.GeneratedMessage
import csw.messages.commands.ControlCommand
import csw.messages.params.generics.Parameter
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsCommandProtos._

object ProtoBuffMsgTransformer {
  def create(loggerFactory: LoggerFactory): ProtoBuffMsgTransformer = ProtoBuffMsgTransformer(loggerFactory)
}

case class ProtoBuffMsgTransformer(loggerFactory: LoggerFactory) extends IMessageTransformer {
  private val log = loggerFactory.getLogger
  import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsCommandProtos.CommandResponse

  override def decode(responsePacket: Array[Byte]): SubystemResponse = {

    log.info(msg = s"Decoding command Response")

    val commandResponse: CommandResponse = CommandResponse.parseFrom(responsePacket)
    val errorState: String               = commandResponse.getErrorState
    if ("OK".equals(errorState)) {
      SubystemResponse(true, None, None)
    }
    SubystemResponse(false, Some(commandResponse.getCmdError.toString), Some(commandResponse.getErrorInfo))
  }
  override def encodeMessage(controlCommand: ControlCommand): Array[Byte] = {
    log.info(msg = s"Encoding command : ${controlCommand} with protobuff convertor")
    controlCommand.commandName.name match {
      case Commands.FOLLOW => {
        getFollowCommandBytes
      }
      case Commands.DATUM => {
        getDatumCommandBytes(controlCommand)
      }
      case Commands.POINT => {
        getPointCommandBytes(controlCommand)
      }
      case Commands.POINT_DEMAND => {
        getPointDemandCommandBytes(controlCommand)
      }
      case Commands.STARTUP => {
        getStartupCommandBytes
      }
      case Commands.SHUTDOWN => {
        getShutdownCommandBytes
      }
    }
  }
  def getFollowCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = FollowCommand.newBuilder().build()
    command.toByteArray
  }
  def getDatumCommandBytes(controlCommand: ControlCommand): Array[Byte] = {
    val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param1                  = axesParam.head
    var axes: Axes              = Axes.BOTH;
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
    var axes: Axes              = Axes.BOTH;
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
