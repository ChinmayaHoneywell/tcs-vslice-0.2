package org.tmt.mcs.subsystem



import java.io.{File, FileOutputStream, PrintStream}
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}

import com.google.protobuf.Timestamp
import com.typesafe.config.Config
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.MCSCommandResponse.CmdError
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.PointDemandCommand
import org.tmt.mcs.subsystem.protos.TcsMcsCommandProtos.MCSCommandResponse
import org.zeromq.ZMQ


object CommandProcessor{
  def create(zmqContext : ZMQ.Context, eventProcessor : EventsProcessor): CommandProcessor = CommandProcessor(zmqContext, eventProcessor)
}
case class CommandProcessor(zmqContext : ZMQ.Context, eventProcessor : EventsProcessor) {
  val pullSocket: ZMQ.Socket = zmqContext.socket(ZMQ.PULL)
  val pushSocket: ZMQ.Socket = zmqContext.socket(ZMQ.PUSH)

  private var azDemanded : Double = 180
  private var elDemanded : Double = 90

 /* val logFilePath : String = System.getenv("LogFiles")
  val realSimCmdFile: File = new File(logFilePath+"/Cmd_RealSim" + System.currentTimeMillis() + "_.txt")
  realSimCmdFile.createNewFile()
  var cmdCounter: Long            = 0
  val cmdPrintStream: PrintStream = new PrintStream(new FileOutputStream(realSimCmdFile))
  this.cmdPrintStream.println("RealSimRecvTimeStamp")*/

  def initialize(config : Config): Unit = {
   // println("Initializing MCS subsystem ZeroMQ command Processor")
   
    val tcsAddress = config.getString("MCS.Simulator.TCSAddress")
    val pullSocketPort = config.getInt("MCS.Simulator.pullSocket")
    val pullSocketAddr = tcsAddress + pullSocketPort
   // println(s"pull socket address is  :$pullSocketAddr")
    pullSocket.connect(pullSocketAddr)

    val mcsAddress = config.getString("MCS.Simulator.MCSAddress")   
    val pushSocketPort = config.getInt("MCS.Simulator.pushSocket")
    val pushSocketAddr = mcsAddress + pushSocketPort
    //println(s"push socket address is : $pushSocketAddr")
    pushSocket.bind(pushSocketAddr)
  }

/*
  def initialize(addr: String, pushSocketPort: Int, pullSocketPort: Int): Unit = {
    println("Initializing MCS subsystem ZeroMQ command Processor")

    val pullSocketAddr = addr + pullSocketPort
    println(s"pull socket address is  :$pullSocketAddr")
    pullSocket.connect(pullSocketAddr)

    val pushSocketAddr = addr + pushSocketPort
    println(s"push socket address is : $pushSocketAddr")
    pushSocket.bind(pushSocketAddr)
  }*/
  def processCommand(): Unit = {
    //println("Process Command Thread Started")
    while(true){
      val commandName: String = pullSocket.recvStr()
      //println(s"Received command is : $commandName")
      if(commandName != null ){
        updateSimulator(commandName)
        val commandData: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
        if (pushSocket.sendMore(commandName)) {
          val commandResponse: MCSCommandResponse = getCommandResponse
         // println(s"$commandName command response is : $commandResponse")
          pushSocket.send(commandResponse.toByteArray,ZMQ.NOBLOCK)
        }else{
          //println(s"Unable to send response for command: $commandName")
        }
        if(commandName == "PointDemand"){
            val pointDemand : PointDemandCommand = PointDemandCommand.parseFrom(commandData)
            azDemanded = pointDemand.getAZ
            elDemanded = pointDemand.getEL
          }
      }else{
       // println("Didn't get any command to process")
      }
    }
 }

  private def getCommandResponse = {
    val instant = Instant.now()
    val commandResponse: MCSCommandResponse = MCSCommandResponse.newBuilder()
      .setCmdError(CmdError.OK)
      .setErrorInfo("No error")
      .setProcessedTime(Timestamp.newBuilder().setSeconds(instant.getEpochSecond).setNanos(instant.getNano))
      .setErrorState(MCSCommandResponse.ErrorState.NONE)
      .build()
    commandResponse
  }

  def updateSimulator(commandName : String):Unit = {
      commandName match {
        case "ReadConfiguration" =>
        //  this.cmdPrintStream.println(getDate(Instant.now()).trim)
          //println(s"Received command : $commandName")
        case "Startup" =>
          eventProcessor.startEventProcessor()

        case "ShutDown" =>
          eventProcessor.updateCurrPosPublisher(false)
          //eventProcessor.updateHealthPublisher(false)
          eventProcessor.updatePosDemandSubscriber(false)
          //println("Updating current position publisher and health publisher to false")
        case _=>
         // println("Not changing publisher thread state")
      }
  }

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  val zoneFormat: String           = "UTC"
  def getDate(instant: Instant) =  LocalDateTime.ofInstant(instant, ZoneId.of(zoneFormat)).format(formatter)
}
