package org.tmt.tcs.mcs.MCSdeploy

import java.net.InetAddress
import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{typed, ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.location.api.models.{AkkaLocation, ComponentId}
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.scaladsl.LoggingSystemFactory
import csw.params.commands.CommandResponse.{Completed, SubmitResponse}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.Prefix
import org.tmt.tcs.mcs.MCSdeploy.constants.DeployConstants

import scala.concurrent.duration._
import scala.concurrent.Await

object MCSCmdApp extends App {
  private val prefix                             = Prefix("tmt.tcs.McsAssembly-Cmd-Client")
  private val clientAppSentTimeKey: Key[Instant] = KeyType.TimestampKey.make("ClientAppSentTime")
  private val system: ActorSystem                = ActorSystemFactory.remote("Client-Cmd-App")
  implicit val mat: ActorMaterializer            = ActorMaterializer()
  private val locationService                    = HttpLocationServiceFactory.makeLocalClient(system, mat)
  private val host                               = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("MCSCmdApp", "0.1", host, system)
  private val readCmdScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  import system._
  implicit val timeout: Timeout                 = Timeout(300.seconds)
  implicit val scheduler: Scheduler             = system.scheduler
  implicit def actorRefFactory: ActorRefFactory = system
  private val sendReadConfCmd: AtomicBoolean    = new AtomicBoolean(true)
  private val commandService                    = getAssembly
  private var cmdCounter: Long                  = 0

  println("Please enter SimulationMode: SimpleSimulator or RealSimulator")
  val simulationMode = scala.io.StdIn.readLine()
  println(s"SimulatorMode selected is : $simulationMode")
  val simulationModeResp: SubmitResponse = sendSimulationModeCommand()
  println(s"SimulationMode command resposne is: $simulationModeResp")

  try {
    val readConfCmdRunner: Runnable = new Runnable {
      override def run(): Unit = {

        println(s"ReadConf commands are: $sendReadConfCmd")
        println(s"Should we send read conf commands ...? : ${sendReadConfCmd.get()}")
        if (sendReadConfCmd.get()) {
          cmdCounter = cmdCounter + 1
          val setup =
            Setup(prefix, CommandName(DeployConstants.READ_CONFIGURATION), None).add(clientAppSentTimeKey.set(Instant.now()))
          println(s"setup is: $setup")
          if (cmdCounter != 200) {
            print(s"command counter is: $cmdCounter and commandService is: $commandService")
            val response = Await.result(commandService.submit(setup), 10.seconds)
            println(s"ReadConf :$cmdCounter response is: $response")
          } else {
            sendReadConfCmd.set(false)
            println("Successfully sent 100 commands to assembly.")
          }
        }
      }
    }

    simulationModeResp match {
      case x: Completed =>
        println(s"Scheduling readConfiguration commands now. : $readCmdScheduler and runner is $readConfCmdRunner")
        //import scala.concurrent.ExecutionContext.Implicits.global
        //scheduler.schedule(5.seconds, 10.seconds, readConfCmdRunner)
        //system.getScheduler.schedule(5.seconds, 10.seconds, readConfCmdRunner)
        readCmdScheduler.scheduleWithFixedDelay(readConfCmdRunner, 10, 10, TimeUnit.SECONDS)
      case _ => println("Unexpected response is received for simulationMode command not starting readConfiguration scheduler.")
    }
  } catch {
    case e: Exception => e.printStackTrace()
  }

  private def getAssembly: CommandService = {
    implicit val sys: typed.ActorSystem[Nothing] = system.toTyped
    val akkaLocations: List[AkkaLocation]        = Await.result(locationService.listByPrefix("tcs.mcs.assembly"), 60.seconds)
    CommandServiceFactory.make(akkaLocations.head)(sys)
  }
  def sendSimulationModeCommand(): SubmitResponse = {
    val simulationModeKey: Key[String]    = KeyType.StringKey.make("SimulationMode")
    val simulModeParam: Parameter[String] = simulationModeKey.set(simulationMode)
    val commandService                    = getAssembly
    val setup                             = Setup(prefix, CommandName("setSimulationMode"), None).add(simulModeParam)
    val response                          = Await.result(commandService.submit(setup), 10.seconds)
    response
  }

}
