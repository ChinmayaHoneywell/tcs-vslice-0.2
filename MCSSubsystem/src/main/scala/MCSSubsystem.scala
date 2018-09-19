import org.tmt.mcs.subsystem.{CommandProcessor, EventsProcessor}
import org.zeromq.ZMQ

object MCSSubsystem extends App{

  println("Welcome to the MCS Simulator")


  val zmqContext : ZMQ.Context = ZMQ.context(1)
  val addr : String = "tcp://localhost:"

  val commandProcessor : CommandProcessor = CommandProcessor.create(zmqContext)
  val pushSocketPort : Int = 55578
  val pullSocketPort : Int = 55579
  commandProcessor.initialize(addr,pushSocketPort, pullSocketPort)

  val eventProcessor : EventsProcessor =  EventsProcessor.createEventsProcessor(zmqContext)
  val pubSocketPort : Int = 55580
  val subSocketPort : Int = 55581
  eventProcessor.initialize(addr,pubSocketPort,subSocketPort)

  eventProcessor.startPublishingCurrPos()
  eventProcessor.startPublishingDiagnosis()
  eventProcessor.startPublishingDriveState()
  eventProcessor.startPublishingHealth()
  
  while(true) {
    commandProcessor.processCommand()
    commandProcessor.publishCurrentPosition()
  }

}
