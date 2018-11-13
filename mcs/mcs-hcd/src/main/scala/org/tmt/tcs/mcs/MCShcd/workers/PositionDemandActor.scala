package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}

import csw.messages.commands.ControlCommand
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.PublishEvent
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{MCSPositionDemand, ParamSetTransformer}

object PositionDemandActor {

  def create(loggerFactory: LoggerFactory,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             paramSetTransformer: ParamSetTransformer): Behavior[ControlCommand] =
    Behaviors.setup(ctx => PositionDemandActor(ctx, loggerFactory, zeroMQProtoActor, paramSetTransformer))
}

/*
This actor will receive position demands a as control command from MCSHandler,
It converts control command into MCSPositionDemands and sends the same to the ZeroMQActor for publishing

 */
case class PositionDemandActor(ctx: ActorContext[ControlCommand],
                               loggerFactory: LoggerFactory,
                               zeroMQProtoActor: ActorRef[ZeroMQMessage],
                               paramSetTransformer: ParamSetTransformer)
    extends MutableBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    log.info(s"Sending position demands: ${msg} to ZeroMQActor for publishing")

    zeroMQProtoActor ! PublishEvent(paramSetTransformer.getMountDemandPositions(msg))
    Behavior.same
  }

}
