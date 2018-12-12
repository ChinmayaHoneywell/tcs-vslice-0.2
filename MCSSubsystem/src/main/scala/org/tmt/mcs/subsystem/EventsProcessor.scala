package org.tmt.mcs.subsystem

import java.lang.Double.{doubleToLongBits, longBitsToDouble}
import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import org.tmt.mcs.subsystem.protos.TcsMcsEventsProtos
import org.tmt.mcs.subsystem.protos.TcsMcsEventsProtos.{McsCurrentPositionEvent, McsDriveStatus, MountControlDiags, TcsPositionDemandEvent}
import org.zeromq.ZMQ

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


  val currentPosPublisher : AtomicBoolean = new AtomicBoolean(true)
  val healthPublisher : AtomicBoolean = new AtomicBoolean(true)
  val posDemandSubScriber : AtomicBoolean = new AtomicBoolean(true)

  def initialize(addr: String, pubSocketPort : Int, subSocketPort : Int) : Unit = {
    val pubSocketAddr = addr + pubSocketPort
    pubSocket.bind(pubSocketAddr)
    println(s"MCS Simulator  is publishing events on : ${pubSocketAddr}")


    val subSocketAddr = addr + subSocketPort
    subSocket.connect(subSocketAddr)
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL)
    println(s"MCS Simulator is subscribing on : ${subSocketAddr}")

  }
  def updateCurrPosPublisher(value : Boolean): Unit ={
    this.currentPosPublisher.set(value)
    println(s"Updating CurrentPosition to : ${this.currentPosPublisher.get()}")
  }
  def updateHealthPublisher(value : Boolean) : Unit = {
    this.healthPublisher.set(value)
    println(s"health publisher value is : ${this.currentPosPublisher.get()}")

  }
  def updatePosDemandSubscriber(value : Boolean): Unit = {
    this.posDemandSubScriber.set(value)
    println(s"PosDemand subscriber value is : ${this.posDemandSubScriber.get()}")
  }

  def startPublishingCurrPos(): Unit ={

   /* var elC: Double = 0
    var azC: Double = 0*/

    while(this.currentPosPublisher.get()){
      Thread.sleep(10)
      //println("Current position publisher thread started publishing")
   /*   def getElCurrent() = {
        if (elC == longBitsToDouble(this.elPosDemand.get())) {
          elC = this.elPosDemand.get()
        } else if (longBitsToDouble(this.elPosDemand.get()) > 0.0) {
          // demanded positions are positive
          elC = elC + 0.05
        } else {
          // for -ve demanded el positions
          elC = elC - 0.05
        }
      }
      def getCurrAz = {
        if (azC == longBitsToDouble(this.azPosDemand.get())) {
          azC = this.azPosDemand.get()
        } else if (longBitsToDouble(this.azPosDemand.get()) > 0.0) {
          //for positive demanded positions
          azC = azC + 0.05
        } else {
          azC = azC - 0.05
        }
      }
      getElCurrent
      getCurrAz*/
      //println(s"Publishing Az position : $azC and el position : $elC demanded az : ${longBitsToDouble(this.azPosDemand.get())}, el : ${longBitsToDouble(this.elPosDemand.get())}")
      val mcsCurrentPosition : McsCurrentPositionEvent =  TcsMcsEventsProtos.McsCurrentPositionEvent.newBuilder()
        .setAzPos(longBitsToDouble(this.azPosDemand.get()))
        .setElPos(longBitsToDouble(this.elPosDemand.get()))
        .setAzPosError(longBitsToDouble(this.azPosDemand.get()) )
        .setElPosError(longBitsToDouble(this.elPosDemand.get()) )
        .setAzInPosition(true)
        .setElInPosition(true)
        .setTime(Instant.now().toEpochMilli)
        //All dummy paramters below
        .setMcsInPosition(true)
        .setAzPosDemand(longBitsToDouble(this.azPosDemand.get()) )
        .setElPosDemand(longBitsToDouble(this.elPosDemand.get()))
        .setEncodeLatchingTime(Instant.now().toEpochMilli)
        .setAzPosDmdErrCount(1)
        .setElPosDmdErrCount(1)
        .setAzWrapPos(longBitsToDouble(this.azPosDemand.get()))
        .setAzWrapPosDemand(longBitsToDouble(this.azPosDemand.get()))
        .setAzWrapPosError(longBitsToDouble(this.azPosDemand.get()))
        .build()

      //println("Publishing currentPosition : "+mcsCurrentPosition)
      if(pubSocket.sendMore("CurrentPosition")){
        println("Sent event: mcsCurrentPosition to MCS")
        if(pubSocket.send(mcsCurrentPosition.toByteArray,ZMQ.NOBLOCK)){
          println(s"Published currentPosition: ${mcsCurrentPosition} event data")
        }else{
          println(s"!!!!!!!! Error occured while publishing current position : $mcsCurrentPosition")
        }
      }else{
        println(s"!!!!!!!! Error occured while publishing current position: $mcsCurrentPosition")
      }
    }
  }
  //TODO : Change the state as per the command executed
  def startPublishingDriveState() : Unit = {
    //println("Publish Drive State Thread started")
    while(true) {
      Thread.sleep(1000)
      val driveStatus : McsDriveStatus = TcsMcsEventsProtos.McsDriveStatus.newBuilder()
        .setAzstate(McsDriveStatus.Azstate.az_following)
        .setElstate(McsDriveStatus.Elstate.el_following)
            .setLifecycle(McsDriveStatus.Lifecycle.running)
        .setTime(Instant.now().toEpochMilli)
        .build()
      if(pubSocket.sendMore("DriveStatus")){
        pubSocket.send(driveStatus.toByteArray,ZMQ.NOBLOCK)
      }
    }
  }
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
  def startPublishingHealth() : Unit = {


    while(this.healthPublisher.get()){
      Thread.sleep(1000)
     // println("Publishing Health information thread started.")
      val mcsHealth = TcsMcsEventsProtos.McsHealth.newBuilder()
        .setHealth(TcsMcsEventsProtos.McsHealth.Health.Good)
        .setReason("All is well")
        .setTime(Instant.now().toEpochMilli)
        .build()

      if(pubSocket.sendMore("Health")){
        if(pubSocket.send(mcsHealth.toByteArray,ZMQ.NOBLOCK)){
         println(s"Successfully published health event ")
        }else{
          println(s"!!!!!!!! Error occured while publishing health information : $mcsHealth")
        }
      }
    }
  }

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
  //Position Demands will be ignored if MCS is not in follow state
  def subscribePositionDemands : Unit = {

    while (this.posDemandSubScriber.get()) {
     // println("Subscribe position Demands thread started")
      val eventName: String = subSocket.recvStr()
    //  println(s"Received : ${eventName} from MCS")
      if (subSocket.hasReceiveMore) {
        val positionDemandBytes: Array[Byte] = subSocket.recv(ZMQ.NOBLOCK)
        val positionDemand: TcsPositionDemandEvent = TcsPositionDemandEvent.parseFrom(positionDemandBytes)
        this.azPosDemand.set(doubleToLongBits(positionDemand.getAzimuth))
        this.elPosDemand.set(doubleToLongBits((positionDemand.getElevation)))
       /* println(s"Start,${longBitsToDouble(this.azPosDemand.get())},${longBitsToDouble(this.elPosDemand.get())},${positionDemand.getTpkPublishTime}," +
          s"${positionDemand.getAssemblyReceivalTime},${positionDemand.getHcdReceivalTime},${System.currentTimeMillis()},Done")*/
      }else{
        println("Didn't get any position demands yet.")
      }
    }
  }

}
