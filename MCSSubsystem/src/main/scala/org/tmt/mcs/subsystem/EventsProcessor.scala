package org.tmt.mcs.subsystem

import java.time.Instant

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

  var AzPosDemanded : Double = 15
  var ElPosDemanded : Double = 15
  var AzCurrPos : Double = 15
  var ElCurrPos : Double = 25
  var demandedTime : Double = 0


  def initialize(addr: String, pubSocketPort : Int, subSocketPort : Int) : Unit = {
    val pubSocketAddr = addr + pubSocketPort
    pubSocket.bind(pubSocketAddr)
    println(s"MCS Simulator  is publishing events on : ${pubSocketAddr}")


    val subSocketAddr = addr + subSocketPort
    subSocket.connect(subSocketAddr)
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL)
    println(s"MCS Simulator is subscribing on : ${subSocketAddr}")

  }
  def startPublishingCurrPos(): Unit ={
    println("Publish Current position thread started")
    while(true){
      Thread.sleep(11000)
      updateCurrentAzPos()
      updateCurrentElPos()
      val mcsCurrentPosition : McsCurrentPositionEvent =  TcsMcsEventsProtos.McsCurrentPositionEvent.newBuilder()
        .setAzPos(AzCurrPos)
        .setElPos(ElCurrPos)
        .setAzPosError(AzPosDemanded - AzCurrPos)
        .setElPosError(ElPosDemanded - ElCurrPos)
        .setAzInPosition(true)
        .setElInPosition(true)
        .setTime(Instant.now().toEpochMilli)
        //All dummy paramters below
        .setMcsInPosition(true)
        .setAzPosDemand(AzPosDemanded)
        .setElPosDemand(ElPosDemanded)
        .setEncodeLatchingTime(Instant.now().toEpochMilli)
        .setAzPosDmdErrCount(1)
        .setElPosDmdErrCount(1)
        .setAzWrapPos(AzCurrPos)
        .setAzWrapPosDemand(AzPosDemanded)
        .setAzWrapPosError(AzPosDemanded - AzCurrPos)
        .build()

      //println("Publishing currentPosition : "+mcsCurrentPosition)
      if(pubSocket.sendMore("CurrentPosition")){
        //println("Sent event: CurrentPosition to MCS")
        if(pubSocket.send(mcsCurrentPosition.toByteArray,ZMQ.NOBLOCK)){
        //  println("Sent currentPosition event data")
        }else{
          //println("Error!!!! Unable to send currentPositionEvent Data.")
        }
      }else{
        //println("Error --> Unable to send currentPosition event name.")
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
  def startPublishingDiagnosis() : Unit = {
    //println("Publish Diagnosis Thread STarted")
    while(true) {
      Thread.sleep(10)
      val diagnosis : MountControlDiags = TcsMcsEventsProtos.MountControlDiags.newBuilder()
        .setAzPosDemand(AzPosDemanded)
        .setElPosDemand(ElPosDemanded)
        .setAzPosError(AzPosDemanded - AzCurrPos)
        .setElPosError(ElPosDemanded - ElCurrPos)
        .build()
      if(pubSocket.sendMore("Diagnosis")){
        pubSocket.send(diagnosis.toByteArray,ZMQ.NOBLOCK)
      }
    }
  }
  def startPublishingHealth() : Unit = {
    //println("Publish Health Thread Started")
    while(true){
      Thread.sleep(12000)
      val mcsHealth = TcsMcsEventsProtos.McsHealth.newBuilder()
        .setHealth(TcsMcsEventsProtos.McsHealth.Health.Good)
        .setReason("All is well")
        .setTime(Instant.now().toEpochMilli)
        .build()
     // println("Publishing Health information.")
      if(pubSocket.sendMore("Health")){
        if(pubSocket.send(mcsHealth.toByteArray,ZMQ.NOBLOCK)){
        //  println("Successfully published health event")
        }
      }
    }
  }

  def updateCurrentElPos() : Double = {
    if(ElCurrPos < ElPosDemanded){
      if(ElCurrPos + 5 < MAX_EL_POS) {
        ElCurrPos = ElCurrPos + 5
      }else{
        ElCurrPos = MAX_AZ_POS
      }
    }
    ElCurrPos
  }
  def updateCurrentAzPos() : Double = {
    if(AzCurrPos < AzPosDemanded){
      if(AzCurrPos + 5 < MAX_AZ_POS) {
        AzCurrPos = AzCurrPos + 5
      }else{
        AzCurrPos = MAX_AZ_POS
      }
    }
    AzCurrPos
  }

  //Position Demands will be ignored if MCS is not in follow state
  def subscribePositionDemands : Unit = {
    println("Subscribe position Demands thread started")
    while (true) {
      val eventName: String = subSocket.recvStr()
    //  println(s"Received : ${eventName} from MCS")
      if (subSocket.hasReceiveMore) {
        val positionDemandBytes: Array[Byte] = subSocket.recv(ZMQ.NOBLOCK)
        val positionDemand: TcsPositionDemandEvent = TcsPositionDemandEvent.parseFrom(positionDemandBytes)
        println(s"Start,${positionDemand.getAzimuth},${positionDemand.getElevation},${positionDemand.getTpkPublishTime},${positionDemand.getAssemblyReceivalTime},${positionDemand.getHcdReceivalTime},${System.currentTimeMillis()},Done")
        println()
        setAzPosDemanded(positionDemand.getAzimuth)
        setElPosDemanded(positionDemand.getElevation)
        //demandedTime = positionDemand.getTime
      }else{
        println("Didn't get any position demands yet.")
      }
    }
  }
  private def setElPosDemanded(elDemanded : Double) ={
    if(elDemanded >= MAX_EL_POS){
      ElPosDemanded = MAX_EL_POS
    }
    else if(elDemanded <= MIN_EL_POS){
      ElPosDemanded = MIN_EL_POS
    }else{
      ElPosDemanded = elDemanded
    }
  }

  private def setAzPosDemanded(azDemanded : Double) = {
    if (azDemanded >= MAX_AZ_POS) {
      AzPosDemanded = MAX_AZ_POS
    }
    else if (azDemanded <= MIN_AZ_POS) {
      AzPosDemanded = MIN_AZ_POS
    } else {
      AzPosDemanded = azDemanded
    }
  }
}
