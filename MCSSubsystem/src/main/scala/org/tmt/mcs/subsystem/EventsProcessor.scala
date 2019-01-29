package org.tmt.mcs.subsystem

import java.io.{File, FileOutputStream, PrintStream}
import java.lang.Double.{doubleToLongBits, longBitsToDouble}
import java.time.format.DateTimeFormatter
import java.time.{Duration, Instant, LocalDateTime, ZoneId}
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.google.protobuf.Timestamp
import com.typesafe.config.Config
import org.tmt.mcs.subsystem.protos.TcsMcsEventsProtos
import org.tmt.mcs.subsystem.protos.TcsMcsEventsProtos.{McsCurrentPositionEvent, McsDriveStatus, MountControlDiags, TcsPositionDemandEvent}
import org.zeromq.ZMQ

import scala.collection.mutable.ListBuffer

object EventsProcessor{
  def createEventsProcessor(zmqContext : ZMQ.Context) : EventsProcessor = EventsProcessor(zmqContext)
}
case class EventsProcessor(zmqContext : ZMQ.Context) {
  val pubSocket : ZMQ.Socket = zmqContext.socket(ZMQ.PUB)
  val subSocket : ZMQ.Socket = zmqContext.socket(ZMQ.SUB)

  val  MIN_AZ_POS : Double = -330
  val  MAX_AZ_POS : Double = 170
  val  MIN_EL_POS: Double  = -3
  val  MAX_EL_POS : Double = 93



  val azPosDemand : AtomicLong = new AtomicLong(doubleToLongBits(0.0))
  val elPosDemand : AtomicLong = new AtomicLong(doubleToLongBits(0.0))
  var scheduler: ScheduledExecutorService = _

  val currentPosPublisher : AtomicBoolean = new AtomicBoolean(true)
  val healthPublisher : AtomicBoolean = new AtomicBoolean(true)
  val posDemandSubScriber : AtomicBoolean = new AtomicBoolean(true)

  def initialize(config: Config): Unit ={
    val mcsAddress = config.getString("MCS.Simulator.MCSAddress")
    val pubSocketPort = config.getInt("MCS.Simulator.pubSocket")
    val pubSocketAddr = mcsAddress + pubSocketPort
    pubSocket.bind(pubSocketAddr)
    //println(s"MCS Simulator  is publishing events on : $pubSocketAddr")

    val tcsAddress = config.getString("MCS.Simulator.TCSAddress")
    val subSocketPort = config.getInt("MCS.Simulator.subSocket")
    val subSocketAddr = tcsAddress + subSocketPort
    subSocket.connect(subSocketAddr)
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL)
    //println(s"MCS Simulator is subscribing on : $subSocketAddr")

  }
/*  def initialize(addr: String, pubSocketPort : Int, subSocketPort : Int) : Unit = {
    val pubSocketAddr = addr + pubSocketPort
    pubSocket.bind(pubSocketAddr)
    println(s"MCS Simulator  is publishing events on : $pubSocketAddr")


    val subSocketAddr = addr + subSocketPort
    subSocket.connect(subSocketAddr)
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL)
    println(s"MCS Simulator is subscribing on : $subSocketAddr")

  }*/
  def startEventProcessor() : Unit = {
    this.scheduler = Executors.newScheduledThreadPool(2)
    startPublishingCurrPos()
    //startPublishingHealth()
    startSubPosDemands()
  }
  def stopEventProcessor() : Unit ={
    this.scheduler = null
    updateCurrPosPublisher(false)
    //updateHealthPublisher(false)
    updatePosDemandSubscriber(false)
  }

  def updateCurrPosPublisher(value : Boolean): Unit = {
    this.currentPosPublisher.set(value)
    //println(s"Updating CurrentPosition to : ${this.currentPosPublisher.get()}")
  }
  /*def updateHealthPublisher(value : Boolean) : Unit = {
    this.healthPublisher.set(value)
    //println(s"health publisher value is : ${this.currentPosPublisher.get()}")
  }*/
  def updatePosDemandSubscriber(value : Boolean): Unit = {
    this.posDemandSubScriber.set(value)
    //println(s"PosDemand subscriber value is : ${this.posDemandSubScriber.get()}")
  }
  val logFilePath : String = System.getenv("LogFiles")


  def startPublishingCurrPos(): Unit = scheduler.scheduleWithFixedDelay(currentPosRunner, 10, 10, TimeUnit.MILLISECONDS)
  val currentPosRunner = new Runnable {
    override def run(): Unit = {
      // log.info(s"Publish Current position thread started")
      var elC: Double = 0
      var azC: Double = 0
      def updateElC() = {
        if (elC >= longBitsToDouble(elPosDemand.get())) {
          elC = elPosDemand.get()
        } else if (longBitsToDouble(elPosDemand.get()) > 0.0) {
          // demanded positions are positive
          elC = elC + 0.0005
        } else {
          // for -ve demanded el positions
          elC = elC - 0.0005
        }
        // log.info(s"Updated el position is : $elC")
      }
      def updateAzC = {
        if (azC >= longBitsToDouble(azPosDemand.get())) {
          azC = azPosDemand.get()
        } else if (longBitsToDouble(azPosDemand.get()) > 0.0) {
          //for positive demanded positions
          azC = azC + 0.0005
        } else {
          azC = azC - 0.0005
        }
      }
      if (currentPosPublisher.get()) {
        updateElC
        updateAzC
        val instant = Instant.now()
        val timeStamp = Timestamp.newBuilder.setSeconds(instant.getEpochSecond).setNanos(instant.getNano).build()

        val mcsCurrentPosition : McsCurrentPositionEvent =  TcsMcsEventsProtos.McsCurrentPositionEvent.newBuilder()
          .setAzPos(azC)
          .setElPos(elC)
          .setAzPosError(azC)
          .setElPosError(elC)
          .setAzInPosition(true)
          .setElInPosition(true)
          .setTime(timeStamp)
          //All dummy paramters below
          .setMcsInPosition(true)
          .setAzPosDemand(azC)
          .setElPosDemand(elC)
          .setEncodeLatchingTime(timeStamp)
          .setAzPosDmdErrCount(1)
          .setElPosDmdErrCount(1)
          .setAzWrapPos(azC)
          .setAzWrapPosDemand(azC)
          .setAzWrapPosError(azC)
          .build()
        try{
          if(pubSocket.sendMore("CurrentPosition")){
            val currPos : Array[Byte] = mcsCurrentPosition.toByteArray
            if(pubSocket.send(currPos)){
              //println(s"Successfully published CurrentPosition event data ${mcsCurrentPosition.getAzPos},${mcsCurrentPosition.getElPos},${mcsCurrentPosition.getTime} and byteArray: $currPos")
            }else{
              //println(s"!!!!!!!! Error occured while publishing current position : $mcsCurrentPosition")
            }
          }else{
            //println(s"!!!!!!!! Error occured while publishing current position: $mcsCurrentPosition")
          }
        }catch{
          case e : Exception =>
            e.printStackTrace()
            //println("------------------- Exception occured while publishing current position event.---------------------------")
        }
       }
    }
  }

 /* def startPublishingDriveState() : Unit = {
    //println("Publish Drive State Thread started")
    while(true) {
      Thread.sleep(1000)
      val instant = Instant.now()
      val timeStamp = Timestamp.newBuilder.setSeconds(instant.getEpochSecond).setNanos(instant.getNano).build()
      val driveStatus : McsDriveStatus = TcsMcsEventsProtos.McsDriveStatus.newBuilder()
        .setAzstate(McsDriveStatus.Azstate.az_following)
        .setElstate(McsDriveStatus.Elstate.el_following)
            .setLifecycle(McsDriveStatus.Lifecycle.running)
        .setTime(timeStamp)
        .build()
      if(pubSocket.sendMore("DriveStatus")){
        pubSocket.send(driveStatus.toByteArray,ZMQ.NOBLOCK)
      }
    }
  }*/
/*  def startPublishingDiagnosis() : Unit = {
    //println("Publish Diagnosis Thread STarted")
    while(true) {
      Thread.sleep(10)
      val diagnosis : MountControlDiags = TcsMcsEventsProtos.MountControlDiags.newBuilder()
        .setAzPosDemand(AzPosDemanded)
        .setElPosDemand(ElPosDemanded)
        .setAzPosError(AzPosDemanded - 0)
        .setElPosError(ElPosDemanded - 0)
        .build()
      if(pubSocket.sendMore("Diagnosis")){
        pubSocket.send(diagnosis.toByteArray,ZMQ.NOBLOCK)
      }
    }
  }*/

  /*val healthRunner : Runnable =  new Runnable {
    override def run(): Unit = {
      if (healthPublisher.get()) {
        val instant = Instant.now()
        val timeStamp = Timestamp.newBuilder.setSeconds(instant.getEpochSecond).setNanos(instant.getNano).build()
        val mcsHealth = TcsMcsEventsProtos.McsHealth.newBuilder()
          .setHealthMCS(TcsMcsEventsProtos.McsHealth.Health.Good)
          .setReason("All is well")
          .setTime(timeStamp)
          .build()

      if(pubSocket.sendMore("Health")){
        val healthBytes = mcsHealth.toByteArray
        //println(s"Sending health events: $mcsHealth with bytes : $healthBytes")
        if(pubSocket.send(healthBytes)){
         // println(s"Successfully published health event ")
          println(s"Sent health event: $mcsHealth with bytes : $healthBytes")
        }else{
          println(s"!!!!!!!! Error occured while publishing health information : $mcsHealth")
        }
      }
    }
   }
  }*/
//  def startPublishingHealth(): Unit = scheduler.scheduleWithFixedDelay(healthRunner, 1000, 1000, TimeUnit.MILLISECONDS)


 /* def getUpdatedCurrentElPos() : Double = {
    if(ElCurrPos < ElPosDemanded){
      if(ElCurrPos + 0.5 < MAX_EL_POS) {
        ElCurrPos = ElCurrPos + 0.5
      }else{
        ElCurrPos = MAX_AZ_POS
      }
    }
    ElCurrPos
  }
  def getUpdatedCurrentAzPos() : Double = {
    if(AzCurrPos < AzPosDemanded){
      if(AzCurrPos + 0.5 < MAX_AZ_POS) {
        AzCurrPos = AzCurrPos + 0.5
      }else{
        AzCurrPos = MAX_AZ_POS
      }
    }
    AzCurrPos
  }
*/

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
  val zoneFormat: String           = "UTC"
 def getDate(instant: Instant) =  LocalDateTime.ofInstant(instant, ZoneId.of(zoneFormat)).format(formatter)

  //Position Demands will be ignored if MCS is not in follow state
  def startSubPosDemands() : Unit = scheduler.execute(posDemandSubscriber)
  private var demandCounter = 0
  private val demandBuffer = new ListBuffer[DemandPosHolder]

  var fileWritten = false
  private val posDemandSubscriber  : Runnable = new Runnable {
    override def run(): Unit = {

      while (posDemandSubScriber.get()) {
        val eventName: String = subSocket.recvStr()
        if (subSocket.hasReceiveMore) {
          val positionDemandBytes: Array[Byte] = subSocket.recv(ZMQ.NOBLOCK)
          demandCounter = demandCounter + 1
          val simulatorRecTime = Instant.now()
          val positionDemand: TcsPositionDemandEvent = TcsPositionDemandEvent.parseFrom(positionDemandBytes)
          azPosDemand.set(doubleToLongBits(positionDemand.getAzimuth))
          elPosDemand.set(doubleToLongBits(positionDemand.getElevation))
          val tpkPublishTimeInstant = Instant.ofEpochSecond(positionDemand.getTpkPublishTime.getSeconds,positionDemand.getTpkPublishTime.getNanos)
          val assemblyRecTimeInstant = Instant.ofEpochSecond(positionDemand.getAssemblyReceivalTime.getSeconds,positionDemand.getAssemblyReceivalTime.getNanos)
          val hcdRecTimeInstant = Instant.ofEpochSecond(positionDemand.getHcdReceivalTime.getSeconds,positionDemand.getHcdReceivalTime.getNanos)

          demandBuffer += DemandPosHolder(tpkPublishTimeInstant,assemblyRecTimeInstant,hcdRecTimeInstant,simulatorRecTime)

          if(demandCounter == 100000 && !fileWritten){
            println("Successfully subscribed 1,00,000 demands")
            try{
              val demandPosLogFile: File = new File(logFilePath+"/PosDemRealLogs_" + System.currentTimeMillis() + ".txt")
              val fileCreated: Boolean = demandPosLogFile.createNewFile()
             // println(s"File $demandPosLogFile create success : $fileCreated, its path is ${demandPosLogFile.getAbsolutePath}")

              val printStream: PrintStream = new PrintStream(new FileOutputStream(demandPosLogFile),true)
              //println("Started writing to position demands file.")
              printStream.println("PK publish timeStamp(t0),Assembly receive timeStamp(t1),HCD receive timeStamp(t2),Simulator receive timeStamp(t3)," +
                "PK to Assembly(t1-t0),Assembly to HCD(t2-t1),HCD to Simulator(t3-t2),Pk to simulator total time(t3-t0)")
              val demandList = demandBuffer.toList
              demandList.foreach(cp=>{
                val pkToAssembly: Double            = Duration.between(cp.pkPublishTime, cp.assemblyRecTime).toNanos.toDouble / 1000000
                val assemblyToHCD: Double       = Duration.between(cp.assemblyRecTime, cp.hcdRecTime).toNanos.toDouble / 1000000
                val hcdToSim: Double = Duration.between(cp.hcdRecTime, cp.simRecTime).toNanos.toDouble / 1000000
                val pkToSim: Double      = Duration.between(cp.pkPublishTime, cp.simRecTime).toNanos.toDouble / 1000000

                val str = s"${getDate(cp.pkPublishTime).trim},${getDate(cp.assemblyRecTime).trim}," +
                  s"${getDate(cp.hcdRecTime).trim},${getDate(cp.simRecTime).trim},${pkToAssembly.toString.trim}," +
                  s"${assemblyToHCD.toString.trim},${hcdToSim.toString.trim},${pkToSim.toString.trim}"
               printStream.println(str)
            })
        	    printStream.flush()
              printStream.close()
              Thread.sleep(1000)
              println(s"Successfully subscribed 1,00,000 demands and written to file: ${demandPosLogFile.getAbsolutePath}")
            }catch{
             case e : Exception => println("Exception occured while writing to file")
                e.printStackTrace()
            }
            fileWritten = true
          }else{
            //println(s"$demandCounter")
          }

        }else{
          //println("Didn't get any position demands yet.")
        }
      }
    }
  }
}
case class DemandPosHolder(pkPublishTime : Instant,assemblyRecTime : Instant,hcdRecTime : Instant,simRecTime:Instant)
