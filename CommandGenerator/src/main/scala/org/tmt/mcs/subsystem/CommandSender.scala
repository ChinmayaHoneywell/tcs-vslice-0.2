package org.tmt.mcs.subsystem

import com.google.protobuf.GeneratedMessage
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.CommandResponse.CmdError
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.{Axes, CommandResponse, DatumCommand, FollowCommand, PointCommand, PointDemandCommand, Startup}
import org.zeromq.ZMQ
object CommandSender{
  def create(zmqContext : ZMQ.Context): CommandSender = CommandSender(zmqContext)
}
case class CommandSender(zmqContext : ZMQ.Context) {
  val pullSocket : ZMQ.Socket =   zmqContext.socket(ZMQ.PULL)
  val pushSocket : ZMQ.Socket = zmqContext.socket(ZMQ.PUSH)
  def initalize(pullSocketAddr: String, pushSocketAddr : String){

    println(s"CommandGenerator pull socket addr. is : ${pullSocketAddr}")
    println(s"CommandGenerator push socket addr is : ${pushSocketAddr}")

    pullSocket.connect(pullSocketAddr)
    pushSocket.bind(pushSocketAddr)
  }
  def sendCommand(commandName :String): Unit ={
    println(s"Submitting command : ${commandName}")
    if(pushSocket.sendMore(commandName)){
      println(s"submiited command name : ${commandName} now submitting command data")
      val commandData : Array[Byte] = getCommandBytes(commandName)
      if(pushSocket.send(commandData,ZMQ.NOBLOCK)) {
        println(s"successfully submitted command : ${commandName} ")
        val commandResponse = readCommandResponse(commandName)
        println(s"Response for command : ${commandName} is  : ${commandResponse}")
      }
    }


  }
  def readCommandResponse(commandName: String): Boolean = {
    val responseCommandName: String = pullSocket.recvStr()
    var responsePacket: Array[Byte] = null
   if(commandName == responseCommandName) {
      println(s"Response for command :${commandName} is received and processing it")
      if (pullSocket.hasReceiveMore) {
        responsePacket = pullSocket.recv(ZMQ.DONTWAIT)
        val commandResponse :CommandResponse = CommandResponse.parseFrom(responsePacket)
        val errorState: CmdError               = commandResponse.getCmdError
        if ("OK".equals(errorState.toString)) {
          return true
        }
        println(s"${commandName} processing failed")
        return false
      }else{
        println(s"No  Response for command :${commandName} ")
      }
    }
    println(s"Response received for some other command : ${responseCommandName} expected for : ${commandName}")
    false
  }
  def getCommandBytes(commandName : String): Array[Byte] ={
      commandName match {
        case "Follow" => {
          getFollowCommandBytes
        }
        case "Datum" => {
          getDatumCommandBytes
        }
        case "Point" => {
          getPointCommandBytes
        }
        case "PointDemand" => {
          getPointDemandCommandBytes
        }
        case "Startup" => {
          getStartupCommandBytes
        }
       /* case "Shutdown" => {
          getShutdownCommandBytes
        }*/
      }
  }
  def getFollowCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = FollowCommand.newBuilder().build()
    command.toByteArray
  }
  def getDatumCommandBytes(): Array[Byte] = {
    val axes: Axes              = Axes.BOTH;
    val command: GeneratedMessage = DatumCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointCommandBytes(): Array[Byte] = {

    var axes: Axes              = Axes.BOTH;
    val command: GeneratedMessage = PointCommand.newBuilder().setAxes(axes).build()
    command.toByteArray
  }
  def getPointDemandCommandBytes(): Array[Byte] = {
    val command: GeneratedMessage = PointDemandCommand.newBuilder().setAZ(30.40).setEL(40.30).build()
    command.toByteArray
  }
  def getStartupCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = Startup.newBuilder().build()
    command.toByteArray
  }
 /* def getShutdownCommandBytes: Array[Byte] = {
    val command: GeneratedMessage = Shutdown.newBuilder().build()
    command.toByteArray
  }*/

}
