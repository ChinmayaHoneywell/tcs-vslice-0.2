package org.tmt.tcs.mcs.MCSassembly

import java.nio.file.{Path, Paths}

import akka.actor.{ActorRefFactory, UnhandledMessage}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import csw.framework.exceptions.FailureStop
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.{InitializeMsg, ShutdownMsg}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

sealed trait LifeCycleMessage
object LifeCycleMessage {
  case class InitializeMsg() extends LifeCycleMessage
  case class ShutdownMsg()   extends LifeCycleMessage
}
object LifeCycleActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   locationService: LocationService,
                   loggerFactory: LoggerFactory): Behavior[LifeCycleMessage] =
    Behaviors.mutable(ctx => LifeCycleActor(ctx, commandResponseManager, locationService, loggerFactory))
}

case class LifeCycleActor(ctx: ActorContext[LifeCycleMessage],
                          commandResponseManager: CommandResponseManager,
                          locationService: LocationService,
                          loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[LifeCycleMessage] {

  private val configClient: ConfigClientService = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  private val log                               = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor     = ctx.executionContext
  override def onMessage(msg: LifeCycleMessage): Behavior[LifeCycleMessage] = {
    msg match {
      case msg: InitializeMsg => doInitialize()
      case msg: ShutdownMsg   => doShutdown()
      case _ => {
        log.info(s"Incorrect message is sent to LifeCycleActor : $msg")
        UnhandledMessage
      }
    }
    this
  }
  /*
    TODO :
   *   1.  initialize MCS HCD and configure assembly with configuration from config server.
   *   2. decide path of MCS configuration file from config server.

   */
  private def doInitialize(): Unit = {
    log.info(msg = " Initializing MCS Assembly actor with the help of LifecycleActor")
    val assemblyConfig: Config = getAssemblyConfig()
    val configValue            = assemblyConfig.getInt("")
    log.info(msg = s"value for key : abc from configuration is $configValue")
    log.info(msg = s"Successfully initialized assembly configuration")
  }
  /*TODO :-
  1. Decide tasks to be done on shutdown
   */
  private def doShutdown(): Unit = {
    log.info(msg = "Shutting down MCS assembly.")
  }
  private def getAssemblyConfig(): Config = {
    val filePath                                 = Paths.get("")
    implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val configData: ConfigData                   = Await.result(getConfigData(filePath), 3.seconds)
    Await.result(configData.toConfigObject, 3.seconds)

  }

  private def getConfigData(filePath: Path): Future[ConfigData] = {
    val futConfigData: Future[Option[ConfigData]] = configClient.getActive(filePath)
    futConfigData flatMap {
      case Some(configData: ConfigData) => {
        //Await.result(configData.toConfigObject, 3.seconds)
        Future.successful(configData)
        //configData
      }
      case None => throw ConfigNotFoundException()
    }
  }

  case class ConfigNotFoundException() extends FailureStop("Failed to find assembly configuration")
}
