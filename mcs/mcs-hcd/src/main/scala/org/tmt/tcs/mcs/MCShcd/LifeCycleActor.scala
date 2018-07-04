package org.tmt.tcs.mcs.MCShcd

import java.nio.file.{Path, Paths}

import akka.actor.{ActorRefFactory}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigValue}
import csw.framework.exceptions.FailureStop
import csw.services.command.CommandResponseManager
import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.config.client.scaladsl.ConfigClientFactory
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.{InitializeMsg, ShutdownMsg}

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
This actor is responsible for initialization of HCD it is called through CSW lifecycle hooks
 */
case class LifeCycleActor(ctx: ActorContext[LifeCycleMessage],
                          commandResponseManager: CommandResponseManager,
                          locationService: LocationService,
                          loggerFactory: LoggerFactory)
    extends MutableBehavior[LifeCycleMessage] {
  implicit val ec: ExecutionContextExecutor     = ctx.executionContext
  private val configClient: ConfigClientService = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)
  private val log                               = loggerFactory.getLogger

  override def onMessage(msg: LifeCycleMessage): Behavior[LifeCycleMessage] = {
    msg match {
      case msg: InitializeMsg => doInitialize()
      case msg: ShutdownMsg   => doShutdown()
      case _ => {
        log.info(s"Incorrect message is sent to LifeCycleActor : $msg")
        Behavior.unhandled
      }
    }
    // this
  }
  /*
    This functions loads configuration from  config server and configures assembly accordingly
   */
  private def doInitialize(): Behavior[LifeCycleMessage] = {
    log.info(msg = " Initializing MCS HCD with the help of Config Server")
    val assemblyConfig: Config = getAssemblyConfig()
    // val config = assemblyConfig.get
    val zeroMQPushSocket: ConfigValue = assemblyConfig.getValue("tmt.tcs.mcs.zeroMQPush")
    log.info(msg = s"push socket is : ${zeroMQPushSocket.toString}")
    log.info(msg = s"zeroMQPushSocket from config file : mcs_hcd.conf is ${zeroMQPushSocket}")
    val zeroMQPullSocket = assemblyConfig.getInt("tmt.tcs.mcs.zeroMQPull")
    log.info(msg = s"zeroMQPullSocket from config file : mcs_hcd.conf is ${zeroMQPullSocket}")
    val zeroMQPubSocket = assemblyConfig.getInt("tmt.tcs.mcs.zeroMQPub")
    log.info(msg = s"zeroMQPubSocket from config file : mcs_hcd.conf is ${zeroMQPubSocket}")
    val zeroMQSubSocket = assemblyConfig.getInt("tmt.tcs.mcs.zeroMQSub")
    log.info(msg = s"zeroMQSubSocket from config file : mcs_hcd.conf is ${zeroMQSubSocket}")

    log.info(msg = s"Successfully initialized assembly configuration")
    Behavior.same
  }
  /*
   This functions shuts down assembly
  */
  private def doShutdown(): Behavior[LifeCycleMessage] = {
    log.info(msg = s"Shutting down MCS assembly.")
    Behavior.same
  }
  private def getAssemblyConfig(): Config = {
    val fileName: String = "org/tmt/tcs/mcs_hcd.conf"
    val filePath         = Paths.get(fileName)
    log.info(msg = s" Loading config file : ${fileName} from config server")
    implicit val context: ActorRefFactory        = ctx.system.toUntyped
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val configData: ConfigData                   = Await.result(getConfigData(filePath), 20.seconds)
    log.info(msg = s" Successfully loaded config file : ${fileName} from config server")
    Await.result(configData.toConfigObject, 3.seconds)

  }

  private def getConfigData(filePath: Path): Future[ConfigData] = {
    configClient.getActive(filePath).flatMap {
      case Some(configData) => {
        //Await.result(configData.toConfigObject, 3.seconds)
        Future.successful(configData)
        //configData
      }
      case None => throw ConfigNotFoundException()
    }
  }

  case class ConfigNotFoundException() extends FailureStop("Failed to find HCD configuration")
}
