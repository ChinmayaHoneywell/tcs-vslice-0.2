package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.ControlCommand
import csw.messages.params.generics.Parameter
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import akka.util.Timeout

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
  implicit val duration: Timeout            = 20 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing Datum command:  ${controlCommand.runId}")
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    log.info(msg = s"In Datum command actor param is : ${axes} and hcdLocation is : ${hcdLocation}")

    hcdLocation match {
      case Some(commandService) => {
        log.info(msg = s"DatumCommandActor sending datum command with parameters :  ${controlCommand} to hcd : ${hcdLocation}")
        //println(commandService)
        println("Before calling commandService ")
        println(commandService.submitAndSubscribe(controlCommand)(10.seconds))
        val response = Await.result(commandService.submitAndSubscribe(controlCommand), 10.seconds)
        log.info(msg = s" updating datum command : ${controlCommand.runId} with response : ${response} ")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        log.info(
          msg =
            s"completed datum command execution for command id : ${controlCommand.runId} and updated its status in commandResponseManager : ${response}"
        )
        Behavior.stopped
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : ${hcdLocation} in DatumCommandActor "))
        Behavior.unhandled
      }
    }
  }
}
