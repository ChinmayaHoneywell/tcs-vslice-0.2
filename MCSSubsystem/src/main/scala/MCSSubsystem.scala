import org.tmt.mcs.subsystem.{CommandProcessor, EventsProcessor}
import org.zeromq.ZMQ

object MCSSubsystem extends App{

  println("Welcome to the MCS Simulator")


  val zmqContext : ZMQ.Context = ZMQ.context(1)
  val addr : String = "tcp://localhost:"



  val eventProcessor : EventsProcessor =  EventsProcessor.createEventsProcessor(zmqContext)
  val pubSocketPort : Int = 55580
  val subSocketPort : Int = 55581
  eventProcessor.initialize(addr,pubSocketPort,subSocketPort)


  val commandProcessor : CommandProcessor = CommandProcessor.create(zmqContext,eventProcessor)
  val pushSocketPort : Int = 55578
  val pullSocketPort : Int = 55579
  commandProcessor.initialize(addr,pushSocketPort, pullSocketPort)

  new Thread(new Runnable {
    override def run(): Unit =  commandProcessor.processCommand()
  }).start()

/*
  new Thread(new Runnable {
    override def run(): Unit = eventProcessor.subscribePositionDemands
  }).start()*/

  //TODO : Temporarily commenting these events need to add more required fields facing
  // mandatory fields missing issues.
  /* new Thread(new Runnable{
     override def run(): Unit = eventProcessor.startPublishingDiagnosis();
   }).start()

    new Thread(new Runnable {
     override def run(): Unit =  eventProcessor.startPublishingDriveState()
   }).start()*/


  


}
