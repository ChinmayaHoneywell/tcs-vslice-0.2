package org.tmt.tcs.mcs.MCSdeploy

import java.net.InetAddress
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{typed, ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.location.api.models.{AkkaLocation, ComponentId}
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.scaladsl.LoggingSystemFactory
import csw.params.commands.CommandResponse.SubmitResponse
import csw.params.commands.{CommandName, CommandResponse, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.Prefix
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object MCSDeployHelper10 {
  def create(simulatorMode: String): MCSDeployHelper10 = MCSDeployHelper10(simulatorMode)
}
case class MCSDeployHelper10(simulatorMode: String) {
  private val system: ActorSystem     = ActorSystemFactory.remote("MCSDeployHelper10")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val locationService         = HttpLocationServiceFactory.makeLocalClient(system, mat)
  private val host                    = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("MCSDeployHelper10", "0.1", host, system)

  import system._
  implicit val timeout: Timeout                 = Timeout(300.seconds)
  implicit val scheduler: Scheduler             = system.scheduler
  implicit def actorRefFactory: ActorRefFactory = system

  private val prefix = Prefix("tmt.tcs.McsAssembly-Client10")

  /**
   * Gets a reference to the running assembly from the location service, if found.
   */
  private def getAssembly: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly1", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly2: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly2", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly3: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly3", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly4: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly4", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly5: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly5", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly6: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly6", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly7: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly7", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly8: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly8", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly9: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly9", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }
  private def getAssembly10: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val connection                               = AkkaConnection(ComponentId("McsAssembly10", Assembly))
    val akkaLocations: Option[AkkaLocation]      = Await.result(locationService.resolve(connection, 60.seconds), 70.seconds)
    CommandServiceFactory.make(akkaLocations.get)(sys)
  }

  def sendStartupCommand()(implicit ec: ExecutionContext): CommandResponse = {
    val setup           = Setup(prefix, CommandName("Startup"), None)
    val commandService1 = getAssembly
    val response        = Await.result(commandService1.submit(setup), 10.seconds)
    //println(s"Startup command1 response is :$response ")

    val commandService2 = getAssembly2
    val response2       = Await.result(commandService2.submit(setup), 10.seconds)
    //println(s"Startup  commandService2 response is :$response2 ")

    val commandService3 = getAssembly3
    val response3       = Await.result(commandService3.submit(setup), 10.seconds)
    //println(s"Startup  commandService3 response is :$response3 ")

    val commandService4 = getAssembly4
    val response4       = Await.result(commandService4.submit(setup), 10.seconds)
    //println(s"Startup commandService4 is :$response4 ")

    val commandService5 = getAssembly5
    val response5       = Await.result(commandService5.submit(setup), 10.seconds)
    //println(s"Startup commandService5 response is :$response5 ")

    val commandService6 = getAssembly6
    val response6       = Await.result(commandService6.submit(setup), 10.seconds)
    //println(s"Startup commandService6 response is :$response6 ")

    val commandService7 = getAssembly7
    val response7       = Await.result(commandService7.submit(setup), 10.seconds)
    //println(s"Startup commandService7 response is :$response7 ")

    val commandService8 = getAssembly8
    val response8       = Await.result(commandService8.submit(setup), 10.seconds)
    //println(s"Startup commandService8 response is :$response8 ")

    val commandService9 = getAssembly9
    val response9       = Await.result(commandService9.submit(setup), 10.seconds)
    //println(s"Startup commandService9 response is :$response9 ")

    val commandService10 = getAssembly10
    val response10       = Await.result(commandService10.submit(setup), 10.seconds)
    //println(s"Startup commandService10 response is :$response10 ")
    response
  }
  def sendSimulationModeCommand(): SubmitResponse = {
    val simulationModeKey: Key[String]    = KeyType.StringKey.make("SimulationMode")
    val simulModeParam: Parameter[String] = simulationModeKey.set(simulatorMode)
    val setup                             = Setup(prefix, CommandName("setSimulationMode"), None).add(simulModeParam)
    val commandService1                   = getAssembly
    val response                          = Await.result(commandService1.submit(setup), 10.seconds)
    //println(s"SimulationMode command1 response is :$response ")

    val commandService2 = getAssembly2
    val response2       = Await.result(commandService2.submit(setup), 10.seconds)
    //println(s"SimulationMode command1 response is :$response2 ")

    val commandService3 = getAssembly3
    val response3       = Await.result(commandService3.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService3 response is :$response3 ")

    val commandService4 = getAssembly4
    val response4       = Await.result(commandService4.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService4 is :$response4 ")

    val commandService5 = getAssembly5
    val response5       = Await.result(commandService5.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService5 response is :$response5 ")

    val commandService6 = getAssembly6
    val response6       = Await.result(commandService6.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService6 response is :$response6 ")

    val commandService7 = getAssembly7
    val response7       = Await.result(commandService7.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService7 response is :$response7 ")

    val commandService8 = getAssembly8
    val response8       = Await.result(commandService8.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService8 response is :$response8 ")

    val commandService9 = getAssembly9
    val response9       = Await.result(commandService9.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService9 response is :$response9 ")

    val commandService10 = getAssembly10
    val response10       = Await.result(commandService10.submit(setup), 10.seconds)
    //println(s"SimulationMode commandService10 response is :$response10 ")

    response
  }

}
