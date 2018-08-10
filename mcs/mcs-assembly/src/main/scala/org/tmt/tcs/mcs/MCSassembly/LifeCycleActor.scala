package org.tmt.tcs.mcs.MCSassembly

import java.nio.file.{Path, Paths}

import akka.actor.{ActorRefFactory, UnhandledMessage}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
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
    Behaviors.setup(ctx => LifeCycleActor(ctx, commandResponseManager, locationService, loggerFactory))
}
/*
This actor is responsible for processing lifecycle commands,
It is called through lifecycle hooks of CSW
 */
case class LifeCycleActor(ctx: ActorContext[LifeCycleMessage],
                          commandResponseManager: CommandResponseManager,
                          locationService: LocationService,
                          loggerFactory: LoggerFactory)
    extends MutableBehavior[LifeCycleMessage] {

  private val configClient: ConfigClientService = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  private val log                               = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor     = ctx.executionContext
  override def onMessage(msg: LifeCycleMessage): Behavior[LifeCycleMessage] = {
    msg match {
      case msg: InitializeMsg => doInitialize()
      case msg: ShutdownMsg   => doShutdown()
      case _ => {
        log.info(s"Incorrect message is sent to LifeCycleActor : $msg")
        Behavior.unhandled
      }
    }
    this
  }
  /*
   This function loads assembly configuration file from config server
   and configures assembly accordingly

   */
  private def doInitialize(): Behavior[LifeCycleMessage] = {
    log.info(msg = " Initializing MCS Assembly actor with the help of LifecycleActor")
    val assemblyConfig: Config = getAssemblyConfig()
    val commandTimeout         = assemblyConfig.getInt("tmt.tcs.mcs.cmdtimeout")
    log.info(msg = s"command timeout duration is seconds ${commandTimeout}")
    val numberOfRetries = assemblyConfig.getInt("tmt.tcs.mcs.retries")
    log.info(msg = s"numberOfRetries for connection between assembly and HCD  is  ${numberOfRetries}")
    val velAccLimit = assemblyConfig.getInt("tmt.tcs.mcs.limit")
    log.info(msg = s"numberOfRetries for connection between assembly and HCD  is  ${velAccLimit}")

    log.info(msg = s"Successfully initialized assembly configuration")
    Behavior.same
  }
  /*TODO :-
  1. Decide tasks to be done on shutdown
   */
  private def doShutdown(): Behavior[LifeCycleMessage] = {
    log.info(msg = "Shutting down MCS assembly.")
    Behavior.stopped
  }
  private def getAssemblyConfig(): Config = {
    val filePath                                 = Paths.get("org/tmt/tcs/mcs_assembly.conf")
    implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val configData: ConfigData                   = Await.result(getConfigData(filePath), 20.seconds)
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
