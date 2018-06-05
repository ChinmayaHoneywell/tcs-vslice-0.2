package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import csw.messages.commands.ControlCommand
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.LoggerFactory

object ReadConfigCmdActor {
  def createObject(commandResponseManager: CommandResponseManager,
                   hcdLocation: Option[CommandService],
                   loggerFactory: LoggerFactory): Behavior[ControlCommand] =
    Behaviors.mutable(ctx => ReadConfigCmdActor(ctx, commandResponseManager, hcdLocation, loggerFactory))
}
case class ReadConfigCmdActor(ctx: ActorContext[ControlCommand],
                              commandResponseManager: CommandResponseManager,
                              hcdLocation: Option[CommandService],
                              loggerFactory: LoggerFactory)
    extends Behaviors.MutableBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(msg = "Processing readConfiguration command ")
    this
  }
}
