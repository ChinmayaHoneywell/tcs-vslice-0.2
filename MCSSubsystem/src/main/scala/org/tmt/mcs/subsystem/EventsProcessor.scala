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

  var  AzPosDemanded : Double = 0
  var  ElPosDemanded : Double = 0

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
    var elC: Double = 0
    var azC: Double = 0
    while(true){
      Thread.sleep(10)
      if(elC < ElPosDemanded){
        elC = elC + 0.5
      }
      if(azC < AzPosDemanded){
        azC = azC + 0.5
      }
      val mcsCurrentPosition : McsCurrentPositionEvent =  TcsMcsEventsProtos.McsCurrentPositionEvent.newBuilder()
        .setAzPos(azC)
        .setElPos(elC)
        .setAzPosError(AzPosDemanded - azC)
        .setElPosError(ElPosDemanded - elC)
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
        .setAzWrapPos(azC)
        .setAzWrapPosDemand(AzPosDemanded)
        .setAzWrapPosError(AzPosDemanded - azC)
        .build()

      //println("Publishing currentPosition : "+mcsCurrentPosition)
      if(pubSocket.sendMore("CurrentPosition")){
        //println("Sent event: CurrentPosition to MCS")
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
  def startPublishingDiagnosis() : Unit = {
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
  }
  def startPublishingHealth() : Unit = {
    //println("Publish Health Thread Started")
    while(true){
      Thread.sleep(1000)
      val mcsHealth = TcsMcsEventsProtos.McsHealth.newBuilder()
        .setHealth(TcsMcsEventsProtos.McsHealth.Health.Good)
        .setReason("All is well")
        .setTime(Instant.now().toEpochMilli)
        .build()
     // println("Publishing Health information.")
      if(pubSocket.sendMore("Health")){
        if(pubSocket.send(mcsHealth.toByteArray,ZMQ.NOBLOCK)){
         println(s"Successfully published health event: $mcsHealth")
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
    println("Subscribe position Demands thread started")
    while (true) {
      val eventName: String = subSocket.recvStr()
    //  println(s"Received : ${eventName} from MCS")
      if (subSocket.hasReceiveMore) {
        val positionDemandBytes: Array[Byte] = subSocket.recv(ZMQ.NOBLOCK)
        val positionDemand: TcsPositionDemandEvent = TcsPositionDemandEvent.parseFrom(positionDemandBytes)
        println(s"Start,${positionDemand.getAzimuth},${positionDemand.getElevation},${positionDemand.getTpkPublishTime},${positionDemand.getAssemblyReceivalTime},${positionDemand.getHcdReceivalTime},${System.currentTimeMillis()},Done")

        AzPosDemanded = positionDemand.getAzimuth
        ElPosDemanded = positionDemand.getElevation
        //demandedTime = positionDemand.getTime
      }else{
        println("Didn't get any position demands yet.")
      }
    }
  }

}
