package org.tmt.mcs.subsystem



import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.CommandResponse.CmdError
import org.zeromq.ZMQ
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.{CommandResponse, FollowCommand, PointCommand, PointDemandCommand}
import org.tmt.mcs.subsystem.protos.TcsMcsEventsProtos.McsCurrentPositionEvent

object CommandProcessor{
  def create(zmqContext : ZMQ.Context): CommandProcessor = CommandProcessor(zmqContext)
}
case class CommandProcessor(zmqContext : ZMQ.Context) {
  val pullSocket: ZMQ.Socket = zmqContext.socket(ZMQ.PULL)
  val pushSocket: ZMQ.Socket = zmqContext.socket(ZMQ.PUSH)
  val pubSocket : ZMQ.Socket = zmqContext.socket(ZMQ.PUB)
  private var publishCurrPos : Boolean = false
  private var azDemanded : Double = 180
  private var elDemanded : Double = 90
  private var azCurr : Double  = 0
  private var elCurr : Double = 0
  def initialize(addr: String, pushSocketPort: Int, pullSocketPort: Int, pubSocketPort : Int): Unit = {
    println("Initializing MCS subsystem ZeroMQ command Processor")

    val pullSocketAddr = addr + pullSocketPort
    println(s"pull socket address is  :${pullSocketAddr}")
    pullSocket.connect(pullSocketAddr)

    val pushSocketAddr = addr + pushSocketPort
    println(s"push socket address is : ${pushSocketAddr}")
    pushSocket.bind(pushSocketAddr)

    val pubSocketAddr = addr + pubSocketPort
    println(s"pub socket port is  : ${pubSocketAddr}")
    pubSocket.bind(pubSocketAddr)
  }

  def processCommand(): Unit = {
    val commandName: String = pullSocket.recvStr()
    println(s"Received command is : ${commandName}")
    if(commandName != null ){
      println(s"Processing ${commandName} command ")
      val commandData: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
      val commandRespone: CommandResponse = CommandResponse.newBuilder().setCmdError(CmdError.OK).setErrorInfo("No error")
        .setProcessedTime(1234.50)
        .setErrorState(CommandResponse.ErrorState.NONE).build()
      if (pushSocket.sendMore(commandName)) {
        println(s"Sending response for : ${commandName} command ")
        pushSocket.send(commandRespone.toByteArray,ZMQ.NOBLOCK)
      }else{
        println(s"Unable to send commandresponse ")
      }

      if(commandName == "Follow" || commandName == "Point" || commandName == "PointDemand"){
        publishCurrPos = true
        if(commandName == "PointDemand"){
          val pointDemand : PointDemandCommand = PointDemandCommand.parseFrom(commandData)
          azDemanded = pointDemand.getAZ
          elDemanded = pointDemand.getEL
        }
      }else{
        publishCurrPos = false
      }
    }else{
      //println("Didn't get any command to process")
    }
 }

  def publishCurrentPosition()={
    while(publishCurrPos){
      if(azCurr < azDemanded) {
         azCurr += 1
      }
      if(elCurr < elDemanded) {
        elCurr += 1
      }
      val currentPosition : McsCurrentPositionEvent =   McsCurrentPositionEvent.newBuilder()
        .setAzPos(azCurr).setAzPosDemand(azDemanded).setAzPosError(azDemanded - azCurr)
        .setElPos(elCurr).setElPosDemand(elDemanded).setElPosError(elDemanded - elCurr)
        .build()
      if(pubSocket.sendMore("CurrentPosition")){
        pubSocket.send(currentPosition.toByteArray,ZMQ.DONTWAIT)
      }
      Thread.sleep(10000)
    }
  }
}
