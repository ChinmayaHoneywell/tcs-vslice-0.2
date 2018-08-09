import org.tmt.mcs.subsystem.CommandProcessor
import org.zeromq.ZMQ

object MCSSubsystem extends App{

  println("Welcome to the MCS Subsystem")
  val ages = Seq(10,20,30,50,40,30,35)
  println(s"oldest person is : ${ages.max}")

  val zmqContext : ZMQ.Context = ZMQ.context(1)

  val commandProcessor : CommandProcessor = CommandProcessor.create(zmqContext)
  val pushSocketPort : Int = 55578
  val pullSocketPort : Int = 55579
  val pubSocketPort : Int = 55580
  commandProcessor.initialize("tcp://localhost:", pushSocketPort, pullSocketPort,pubSocketPort)
  while(true) {
    commandProcessor.processCommand()
    commandProcessor.publishCurrentPosition()
  }

}
