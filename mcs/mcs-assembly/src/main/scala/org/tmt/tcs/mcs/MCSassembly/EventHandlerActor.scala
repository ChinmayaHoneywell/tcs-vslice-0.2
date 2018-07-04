package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.EventMessage.{EventPublishMsg, EventSubscribeMsg}

sealed trait EventMessage

object EventMessage {
  case class EventPublishMsg()   extends EventMessage
  case class EventSubscribeMsg() extends EventMessage
}

object EventHandlerActor {
  def createObject(loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(ctx => EventHandlerActor(ctx: ActorContext[EventMessage], loggerFactory: LoggerFactory))
}
/*
This actor is responsible consuming incoming events to MCS Assembly and publishing outgoing
events from MCS Assembly
 */
case class EventHandlerActor(ctx: ActorContext[EventMessage], loggerFactory: LoggerFactory)
    extends MutableBehavior[EventMessage] {

  private val log = loggerFactory.getLogger

  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    msg match {
      case x: EventPublishMsg   => publishEventMsg(x)
      case x: EventSubscribeMsg => subscribeEventMsg(x)
    }
  }
  /*
   * TODO : publish all events to channel, Decide channel on which events should be published and subscribed
   */
  def publishEventMsg(x: EventPublishMsg): Behavior[EventMessage] = {
    log.info(msg = s"Publishing message $x")
    this
  }
  /*
   * TODO : subscribe to position demands from TPK, current position,status events and publish them
   */
  def subscribeEventMsg(x: EventSubscribeMsg): Behavior[EventMessage] = {
    log.info(msg = s"Received message : $x")
    Behavior.same
  }

}
