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
  val pubSocketPort : Int = 55581
  val subSocketPort : Int = 55580
  eventProcessor.initialize(addr,pubSocketPort,subSocketPort)

  new Thread(new Runnable {
    override def run(): Unit =
      commandProcessor.processCommand();
  }).start()

  new Thread(new Runnable {
    override def run(): Unit =
      eventProcessor.startPublishingCurrPos();
  }).start()
    //TODO : Temporarily commenting these events need to add more required fields facing
    // mandatory fields missing issues.
 /* new Thread(new Runnable{
    override def run(): Unit = eventProcessor.startPublishingDiagnosis();
  }).start()

   new Thread(new Runnable {
    override def run(): Unit =  eventProcessor.startPublishingDriveState()
  }).start()*/

   new Thread(new Runnable {
    override def run(): Unit = eventProcessor.startPublishingHealth()
  }).start();




  


}
