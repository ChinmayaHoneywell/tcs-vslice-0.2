package org.tmt.tcs.mcs.MCSdeploy

import java.net.InetAddress
import java.time.Instant
import java.util.Calendar

import akka.actor.{typed, ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import csw.messages.location.ComponentId
import csw.messages.location.ComponentType.Assembly
import csw.messages.location.Connection.AkkaConnection
import akka.actor.typed.scaladsl.adapter._
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandName, CommandResponse, Setup}
import csw.messages.events.{Event, SystemEvent}
import csw.messages.params.generics.{Key, KeyType, Parameter}
import csw.messages.params.models.{Id, Prefix}
import csw.services.command.scaladsl.CommandService
import csw.services.event.EventServiceFactory
import csw.services.event.api.scaladsl.{EventService, EventSubscriber}
import csw.services.location.commons.ClusterAwareSettings
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl.LoggingSystemFactory
import org.tmt.tcs.mcs.MCSdeploy.constants.{DeployConstants, EventConstants}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
/*
This object acts as a client object to test execution of commands

 */
object MCSMainApp extends App {
  private val system: ActorSystem = ClusterAwareSettings.system
  private val locationService     = LocationServiceFactory.withSystem(system)

  private val maybeObsId = None
  private val host       = InetAddress.getLocalHost.getHostName

  LoggingSystemFactory.start("MCSMainApp", "0.1", host, system)

  import system._

  implicit val timeout: Timeout                 = Timeout(300.seconds)
  implicit val scheduler: Scheduler             = system.scheduler
  implicit def actorRefFactory: ActorRefFactory = system
  implicit val mat: ActorMaterializer           = ActorMaterializer()
  // implicit val ec: ExecutionContextExecutor     = system.dispatcher
  private val connection = AkkaConnection(ComponentId("McsAssembly", Assembly))

  private val axesKey: Key[String] = KeyType.StringKey.make("axes")
  private val azKey: Key[Double]   = KeyType.DoubleKey.make("AZ")
  private val elKey: Key[Double]   = KeyType.DoubleKey.make("EL")

  /**
   * Gets a reference to the running assembly from the location service, if found.
   */
  private def getAssembly: Future[Option[CommandService]] = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    locationService.resolve(connection, 3000.seconds).map(_.map(new CommandService(_)))
  }
  private def getEventService(): EventService = {
    locationService.resolve(connection, 3000.seconds)
    val eventService: EventService = new EventServiceFactory().make(locationService)(system)
    eventService
  }

  val prefix = Prefix("tmt.tcs.McsAssembly-Client")

//  var count: Integer = 0
  var simulationMode = "SimpleSimulator"

  var simulCmdSentTime: Long = System.currentTimeMillis()
  val resp0                  = Await.result(sendSimulationModeCommand(simulationMode), 10.seconds)
  println(s"SimulationMode command response is : $resp0 total time taken is : ${System.currentTimeMillis() - simulCmdSentTime}")

  var startupSentTime: Long = System.currentTimeMillis()
  val resp1                 = Await.result(sendStartupCommand, 30.seconds)
  println(s"Startup command response is : $resp1 total time taken is : ${System.currentTimeMillis() - startupSentTime}")

  var datumCommandSentTime: Long = System.currentTimeMillis()
  val resp2                      = Await.result(sendDatumCommand, 200.seconds)
  println(s"Datum command response is : $resp2 total time taken is : ${System.currentTimeMillis() - datumCommandSentTime}")

  var followCmdSentTime: Long = System.currentTimeMillis()
  val resp3                   = Await.result(sendFollowCommand, 200.seconds)
  println(s"Follow command response is : $resp3 total time taken is : ${System.currentTimeMillis() - followCmdSentTime}")

  // val resp4 = Await.result(sendMoveCommand, 250.seconds)
  //println(s"Move command response is : $resp4 at : ${System.currentTimeMillis()}")

  var dummyImmCmd: Long = System.currentTimeMillis()
  val resp5             = Await.result(sendDummyImmediateCommand, 200.seconds)
  println(s"Dummy immediate command Response is : ${resp5} total time taken is : ${System.currentTimeMillis() - dummyImmCmd}")

  var dummyLongCmd: Long = System.currentTimeMillis()
  val resp6              = Await.result(sendDummyLongCommand, 200.seconds)
  println(s"Dummy Long Command Response is : ${resp6} total time taken is : ${System.currentTimeMillis() - dummyLongCmd}")

  /*var shutdownCmd: Long = System.currentTimeMillis()
  val resp7             = Await.result(sendShutDownCmd, 30.seconds)
  println(s"Shutdown Command Response is : ${resp7} total time taken is : ${System.currentTimeMillis() - shutdownCmd}")*/

  println(
    s"===========================================Command set completed ============================================================================="
  )

  new Thread(new Runnable { override def run(): Unit = startSubscribingEvents }).start()

  def startSubscribingEvents(): Unit = {
    // println(" ** Started subscribing Events from Assembly ** ")
    val eventService = getEventService
    val subscriber   = eventService.defaultSubscriber
    subscriber.subscribeCallback(DeployConstants.currentPositionSet, event => processCurrentPosition(event))
    subscriber.subscribeCallback(DeployConstants.healthSet, event => processHealth(event))
    subscriber.subscribeCallback(DeployConstants.dummyEventKey, event => proecessDummyEvent(event))
  }
  def proecessDummyEvent(event: Event): Future[_] = {
    //println(s"** Received event : ${event} from Assembly. ** ")
    Future.successful[String]("Successfully processed Dummy event from assembly")
  }
  def processHealth(event: Event): Future[_] = {
    // println(s"*** Received health event: ${event} from assembly at time : ${Calendar.getInstance().getTime} *** ")
    val clientAppRecTime = System.currentTimeMillis()
    event match {
      case systemEvent: SystemEvent => {
        val params                               = systemEvent.paramSet
        val simulatorSentTimeParam: Parameter[_] = params.find(msg => msg.keyName == EventConstants.TIMESTAMP).get
        val simulatorPublishTime                 = simulatorSentTimeParam.head
        val hcdReceiveTime                       = params.find(msg => msg.keyName == EventConstants.HCD_EventReceivalTime).get.head
        val assemblyRecTime                      = params.find(msg => msg.keyName == EventConstants.ASSEMBLY_EVENT_RECEIVAL_TIME).get.head
        //println(s"Health, ${simulatorPublishTime}, ${hcdReceiveTime}, ${assemblyRecTime}, ${clientAppRecTime}")
      }
    }
    Future.successful[String]("Successfully processed Health event from assembly")
  }
  def processCurrentPosition(event: Event): Future[_] = {
    val clientAppRecTime = System.currentTimeMillis()
    // println(s"** Received current position Event : ${event} at client app receival time is : ${today} ** ")
    event match {
      case systemEvent: SystemEvent => {
        val params                               = systemEvent.paramSet
        val azPosParam: Parameter[_]             = params.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS).get
        val elPosParam: Parameter[_]             = params.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS).get
        val simulatorSentTimeParam: Parameter[_] = params.find(msg => msg.keyName == EventConstants.TIMESTAMP).get
        val simulatorPublishTime                 = simulatorSentTimeParam.head
        val hcdReceiveTime                       = params.find(msg => msg.keyName == EventConstants.HCD_EventReceivalTime).get.head
        val assemblyRecTime                      = params.find(msg => msg.keyName == EventConstants.ASSEMBLY_EVENT_RECEIVAL_TIME).get.head
        println(
          s"CurrentPosition:, ${azPosParam}, ${elPosParam},  ${simulatorPublishTime},${hcdReceiveTime}, ${assemblyRecTime},${clientAppRecTime}"
        )
      }
    }
    Future.successful[String]("Successfully processed Current position event from assembly")
  }

  def sendDummyImmediateCommand()(implicit ec: ExecutionContext): Future[CommandResponse] = {
    getAssembly.flatMap {
      case Some(commandService) => {
        val dummyImmediate = Setup(prefix, CommandName("DummyImmediate"), None)
        commandService.submit(dummyImmediate)
      }
      case None => {
        Future.failed[CommandResponse](new Exception("Can't locate MCSAssembly"))
      }
    }
  }
  def sendDummyLongCommand()(implicit ex: ExecutionContext): Future[CommandResponse] = {
    getAssembly.flatMap {
      case Some(commandService) => {
        val dummyLong = Setup(prefix, CommandName("DummyLong"), None)
        commandService.submitAndSubscribe(dummyLong)
      }
      case None => {
        Future.failed[CommandResponse](new Exception("Can't locate MCSAssembly"))
      }
    }
  }

  def sendStartupCommand()(implicit ec: ExecutionContext): Future[CommandResponse] = {
    getAssembly.flatMap {
      case Some(commandService) => {
        val setup = Setup(prefix, CommandName("Startup"), None)
        commandService.submit(setup)
      }
      case None => {
        Future.successful(Error(Id(), "Can't locate MCSAssembly"))
      }
    }
  }
  def sendShutDownCmd()(implicit ec: ExecutionContext): Future[CommandResponse] = {
    getAssembly.flatMap {
      case Some(commandService) => {
        val setup = Setup(prefix, CommandName("ShutDown"), None)
        commandService.submit(setup)
      }
      case None => {
        Future.successful(Error(Id(), "Can't locate MCSAssembly"))
      }
    }
  }
  def sendDatumCommand(): Future[CommandResponse] = {
    val datumParam: Parameter[String] = axesKey.set("BOTH")
    getAssembly.flatMap {
      case Some(commandService) => {
        val setup = Setup(prefix, CommandName("Datum"), None).add(datumParam)
        commandService.submitAndSubscribe(setup)
      }
      case None => {
        Future.successful(Error(Id(), "Can't locate MCSAssembly"))
      }
    }
  }
  def sendFollowCommand(): Future[CommandResponse] = {

    getAssembly.flatMap {
      case Some(commandService) =>
        val setup = Setup(prefix, CommandName("Follow"), None)

        commandService.submitAndSubscribe(setup)
      case None =>
        Future.successful(Error(Id(), "Can't locate TcsTemplateAssembly"))
    }
  }
  def sendSimulationModeCommand(simulationMode: String): Future[CommandResponse] = {
    val simulationModeKey: Key[String] = KeyType.StringKey.make("SimulationMode")
    getAssembly.flatMap {
      case Some(commandService) => {
        val simulModeParam: Parameter[String] = simulationModeKey.set(simulationMode)
        val setup                             = Setup(prefix, CommandName("setSimulationMode"), None).add(simulModeParam)
        commandService.submitAndSubscribe(setup)

      }
    }
  }
  def sendMoveCommand(): Future[CommandResponse] = {
    val axesParam: Parameter[String] = axesKey.set("BOTH")
    val azParam: Parameter[Double]   = azKey.set(1.5)
    val elParam: Parameter[Double]   = elKey.set(10)

    getAssembly.flatMap {
      case Some(commandService) =>
        val setup = Setup(prefix, CommandName("Move"), None)
          .add(axesParam)
          .add(azParam)
          .add(elParam)
        //println("Move command sent time : " + System.currentTimeMillis())
        commandService.submitAndSubscribe(setup)
      case None =>
        Future.successful(Error(Id(), "Can't locate TcsTemplateAssembly"))
    }
  }

}
