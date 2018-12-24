package org.tmt.mcs.subsystem

import java.lang.Double.{doubleToLongBits, longBitsToDouble}
import java.time.Instant
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.google.protobuf.Timestamp
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
    println(s"MCS Simulator  is publishing events on : $pubSocketAddr")


    val subSocketAddr = addr + subSocketPort
    subSocket.connect(subSocketAddr)
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL)
    println(s"MCS Simulator is subscribing on : $subSocketAddr")

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

    var elC: Double = 0
    var azC: Double = 0
    def updateElC() = {
      if (elC >= longBitsToDouble(this.elPosDemand.get())) {
        elC = this.elPosDemand.get()
      } else if (longBitsToDouble(this.elPosDemand.get()) > 0.0) {
        // demanded positions are positive
        elC = elC + 0.0005
      } else {
        // for -ve demanded el positions
        elC = elC - 0.0005
      }
      println(s"Updated el position is : $elC")
    }
    def updateAzC = {
      if (azC >= longBitsToDouble(this.azPosDemand.get())) {
        azC = this.azPosDemand.get()
      } else if (longBitsToDouble(this.azPosDemand.get()) > 0.0) {
        //for positive demanded positions
        azC = azC + 0.0005
      } else {
        azC = azC - 0.0005
      }
      println(s"Updated az position is : $azC")
    }
    while(this.currentPosPublisher.get()){
      Thread.sleep(100)
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

      //println("Publishing currentPosition : "+mcsCurrentPosition)
      if(pubSocket.sendMore("CurrentPosition")){
        println("Sent event: mcsCurrentPosition to MCS")
        if(pubSocket.send(mcsCurrentPosition.toByteArray,ZMQ.NOBLOCK)){
          println(s"Published currentPosition: $mcsCurrentPosition event data")
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
      val instant = Instant.now()
      val timeStamp = Timestamp.newBuilder.setSeconds(instant.getEpochSecond).setNanos(instant.getNano).build()
      val mcsHealth = TcsMcsEventsProtos.McsHealth.newBuilder()
        .setHealth(TcsMcsEventsProtos.McsHealth.Health.Good)
        .setReason("All is well")
        .setTime(timeStamp)
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
  def subscribePositionDemands() : Unit = {
    while (this.posDemandSubScriber.get()) {
      val eventName: String = subSocket.recvStr()
      if (subSocket.hasReceiveMore) {
        val positionDemandBytes: Array[Byte] = subSocket.recv(ZMQ.NOBLOCK)
        val positionDemand: TcsPositionDemandEvent = TcsPositionDemandEvent.parseFrom(positionDemandBytes)
        this.azPosDemand.set(doubleToLongBits(positionDemand.getAzimuth))
        this.elPosDemand.set(doubleToLongBits(positionDemand.getElevation))
       println(s"Start,${longBitsToDouble(this.azPosDemand.get())},${longBitsToDouble(this.elPosDemand.get())},${positionDemand.getTpkPublishTime}," +
          s"${positionDemand.getAssemblyReceivalTime},${positionDemand.getHcdReceivalTime},${System.currentTimeMillis()},Done")
      }else{
        println("Didn't get any position demands yet.")
      }
    }
  }

}
