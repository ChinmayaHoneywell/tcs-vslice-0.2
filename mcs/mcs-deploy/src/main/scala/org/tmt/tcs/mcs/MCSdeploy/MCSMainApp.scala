package org.tmt.tcs.mcs.MCSdeploy

import java.net.InetAddress

import akka.actor.{typed, ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import csw.messages.location.ComponentId
import csw.messages.location.ComponentType.Assembly
import csw.messages.location.Connection.AkkaConnection
import akka.actor.typed.scaladsl.adapter._
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandName, CommandResponse, Setup}
import csw.messages.params.generics.{Key, KeyType, Parameter}
import csw.messages.params.models.{Id, Prefix}
import csw.services.command.scaladsl.CommandService
import csw.services.location.commons.ClusterAwareSettings
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl.LoggingSystemFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
/*
This object acts as a client object to test execution of commands

 */
object MCSMainApp extends App {
  private val system: ActorSystem = ClusterAwareSettings.system
  private val locationService     = LocationServiceFactory.withSystem(system)
  //private val tcsTemplateClient   = TcsTemplateClient(Prefix("tcs.tcs-template"), system, locationService)
  private val maybeObsId = None
  private val host       = InetAddress.getLocalHost.getHostName

  LoggingSystemFactory.start("MCSMainApp", "0.1", host, system)

  import system._

  implicit val timeout: Timeout                 = Timeout(300.seconds)
  implicit val scheduler: Scheduler             = system.scheduler
  implicit def actorRefFactory: ActorRefFactory = system
  implicit val mat: ActorMaterializer           = ActorMaterializer()
  //implicit val ec: ExecutionContextExecutor     = system.dispatcher
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
  val prefix = Prefix("tmt.tcs.McsAssembly-Client")

  val resp1 = Await.result(sendStartupCommand, 30.seconds)
  println(s"Startup command response is : $resp1")

  val resp2 = Await.result(sendDatumCommand, 200.seconds)
  println(s"Datum command response is : $resp2")

  val resp3 = Await.result(sendFollowCommand, 200.seconds)
  println(s"Follow command response is : $resp3")

  // val resp4 = Await.result(sendMoveCommand, 250.seconds)
  //println(s"Move command response is : $resp4")

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
        commandService.submitAndSubscribe(setup)
      case None =>
        Future.successful(Error(Id(), "Can't locate TcsTemplateAssembly"))
    }
  }

}
