package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.util.Timeout
object MoveCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.mutable(ctx => MoveCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
case class MoveCommandActor(ctx: ActorContext[ControlCommand],
                            commandResponseManager: CommandResponseManager,
                            hcdLocation: Option[CommandService],
                            loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[ControlCommand] {
  private val log                = loggerFactory.getLogger
  private val mcsHCDPrefix       = Prefix(Subsystem.MCS, "tmt.tcs.mcs.assembly")
  implicit val duration: Timeout = 20 seconds
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing Move command ${controlCommand}")

    val axesParam = controlCommand.paramSet.find(x => x.keyName == "axes").get
    val azParam   = controlCommand.paramSet.find(x => x.keyName == "az").get
    val elParam   = controlCommand.paramSet.find(x => x.keyName == "el").get

    val pointSetup = Setup(mcsHCDPrefix, CommandName(Commands.POINT), controlCommand.maybeObsId)
      .add(axesParam)

    val pointDemandSetup = Setup(mcsHCDPrefix, CommandName(Commands.POINTDEMAND), controlCommand.maybeObsId)
      .add(azParam)
      .add(elParam)

    hcdLocation match {
      case Some(commandService) => {
        val response = Await.result(commandService.submitAllAndGetFinalResponse(Set(pointSetup, pointDemandSetup)), 3.seconds)

        log.info(msg = s" updating move command  ${controlCommand.runId} with response : ${response} ")
        commandResponseManager.addOrUpdateCommand(controlCommand.runId, response)
        log.info(msg = s"completed move command execution for command id : $controlCommand.runId")
        this
      }
      case None => {
        Future.successful(Error(Id(), s"Can't locate mcs hcd location : $hcdLocation"))
        Behavior.unhandled
      }
    }

  }
}
