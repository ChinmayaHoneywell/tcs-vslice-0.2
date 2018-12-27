package org.tmt.mcs.subsystem



import java.io.{File, FileOutputStream, PrintStream}
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}

import com.google.protobuf.Timestamp
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

  val realSimCmdFile: File = new File("/home/tmt_tcs_2/LogFiles/scenario3/Cmd_RealSim" + System.currentTimeMillis() + "_.txt")
  realSimCmdFile.createNewFile()
  var cmdCounter: Long            = 0
  val cmdPrintStream: PrintStream = new PrintStream(new FileOutputStream(realSimCmdFile))
  this.cmdPrintStream.println("RealSimRecvTimeStamp")

  def initialize(addr: String, pushSocketPort: Int, pullSocketPort: Int): Unit = {
    println("Initializing MCS subsystem ZeroMQ command Processor")

    val pullSocketAddr = addr + pullSocketPort
    println(s"pull socket address is  :$pullSocketAddr")
    pullSocket.connect(pullSocketAddr)

    val pushSocketAddr = addr + pushSocketPort
    println(s"push socket address is : $pushSocketAddr")
    pushSocket.bind(pushSocketAddr)
  }

  def processCommand(): Unit = {
    println("Process Command Thread Started")
    while(true){
      val commandName: String = pullSocket.recvStr()
      println(s"Received command is : $commandName")
      if(commandName != null ){
        println(s"Processing $commandName command ")
        updateSimulator(commandName)
        val commandData: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
        if (pushSocket.sendMore(commandName)) {
          println(s"Sending response for : $commandName command ")
          val instant = Instant.now()
          val commandResponse: MCSCommandResponse = MCSCommandResponse.newBuilder()
            .setCmdError(CmdError.OK)
            .setErrorInfo("No error")
            .setProcessedTime(Timestamp.newBuilder().setSeconds(instant.getEpochSecond).setNanos(instant.getNano))
            .setErrorState(MCSCommandResponse.ErrorState.FAILED)
            .build()
          println(s"$commandName command response is : $commandResponse")
          pushSocket.send(commandResponse.toByteArray,ZMQ.NOBLOCK)
        }else{
          println(s"Unable to send command response ")
        }
        if(commandName == "PointDemand"){
            val pointDemand : PointDemandCommand = PointDemandCommand.parseFrom(commandData)
            azDemanded = pointDemand.getAZ
            elDemanded = pointDemand.getEL
          }
      }else{
        println("Didn't get any command to process")
      }
    }
 }
  def updateSimulator(commandName : String):Unit = {
      commandName match {
        case "ReadConfiguration" =>
          this.cmdPrintStream.println(getDate(Instant.now()).trim)
        case "Startup" =>
        eventProcessor.startPublishingCurrPos()
          eventProcessor.startPublishingHealth()
        case "ShutDown" =>
          eventProcessor.updateCurrPosPublisher(false)
          eventProcessor.updateHealthPublisher(false)
          eventProcessor.updatePosDemandSubscriber(false)
          println("Updating current position publisher and health publisher to false")
        case _=>
          println("Not changing publisher thread state")
      }
  }

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  val zoneFormat: String           = "UTC"
  def getDate(instant: Instant) =  LocalDateTime.ofInstant(instant, ZoneId.of(zoneFormat)).format(formatter)
}
