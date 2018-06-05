package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.ControlCommand
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory

object ServoCommandActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.mutable(ctx => ServoCommandActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
case class ServoCommandActor(ctx: ActorContext[ControlCommand],
                             commandResponseManager: CommandResponseManager,
                             hcdLocation: Option[CommandService],
                             loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger
  override def onMessage(controlCommand: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = s"Executing ServoOff command ${controlCommand}")
    this
  }
}
