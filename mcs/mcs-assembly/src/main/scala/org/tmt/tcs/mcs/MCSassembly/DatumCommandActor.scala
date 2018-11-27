package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.ControlCommand
import csw.messages.params.generics.Parameter
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.{CommandService}
import csw.services.logging.scaladsl.LoggerFactory

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import csw.services.command.CommandResponseManager

object DatumCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.setup(ctx => DatumCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
/*
This actor is responsible for processing of datum command.
 */
case class DatumCommandActor(ctx: ActorContext[ControlCommand],
                             commandResponseManager: CommandResponseManager,
                             hcdLocation: Option[CommandService],
                             loggerFactory: LoggerFactory)
    extends MutableBehavior[ControlCommand] {
  private val log                           = loggerFactory.getLogger
  private val mcsHCDPrefix                  = Prefix(Subsystem.MCS.toString)
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val duration: Timeout            = 60 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submitAndSubscribe(controlCommand), 50.seconds)
        log.info(s"Response for Datum command in Assembly is : ${response}")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        Behavior.stopped
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : ${hcdLocation} in DatumCommandActor "))
        Behavior.unhandled
      }
    }
  }
}
