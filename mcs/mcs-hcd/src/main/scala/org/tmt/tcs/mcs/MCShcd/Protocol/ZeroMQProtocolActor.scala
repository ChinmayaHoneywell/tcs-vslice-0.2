package org.tmt.tcs.mcs.MCShcd.Protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import com.typesafe.config.Config
import csw.messages.commands.{CommandIssue, CommandResponse, ControlCommand}
import csw.messages.params.models.Id
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage._
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants
import org.tmt.tcs.mcs.MCShcd.msgTransformers._
import org.zeromq.ZMQ
sealed trait ZeroMQMessage
object ZeroMQMessage {

  case class InitializeSubsystem(config: Config)                                            extends ZeroMQMessage
  case class SubmitCommand(sender: ActorRef[ZeroMQMessage], controlCommand: ControlCommand) extends ZeroMQMessage
  case class MCSResponse(commandResponse: CommandResponse)                                  extends ZeroMQMessage
  case class PublishEvent(mcsPositionDemands: MCSPositionDemand)                            extends ZeroMQMessage
  case class StartEventSubscription()                                                       extends ZeroMQMessage
}
object ZeroMQProtocolActor {
  def create(statePublisherActor: ActorRef[EventMessage], loggerFactory: LoggerFactory): Behavior[ZeroMQMessage] =
    Behaviors.setup(ctx => ZeroMQProtocolActor(ctx, statePublisherActor, loggerFactory))
}
case class ZeroMQProtocolActor(ctx: ActorContext[ZeroMQMessage],
                               statePublisherActor: ActorRef[EventMessage],
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[ZeroMQMessage] {
  private val log: Logger                              = loggerFactory.getLogger
  private val zmqContext: ZMQ.Context                  = ZMQ.context(1)
  private val pushSocket: ZMQ.Socket                   = zmqContext.socket(ZMQ.PUSH) //55579
  private val pullSocket: ZMQ.Socket                   = zmqContext.socket(ZMQ.PULL) //55578
  private val subscribeSocket: ZMQ.Socket              = zmqContext.socket(ZMQ.SUB) //55580
  private val pubSocket: ZMQ.Socket                    = zmqContext.socket(ZMQ.PUB) //55581
  private val addr: String                             = new String("tcp://localhost:")
  private val messageTransformer: IMessageTransformer  = ProtoBuffMsgTransformer.create(loggerFactory)
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)

  override def onMessage(msg: ZeroMQMessage): Behavior[ZeroMQMessage] = {
    msg match {
      case msg: InitializeSubsystem => {
        initMCSConnection(msg.config)
        Behavior.same
      }
      case msg: SubmitCommand => {
        submitCommandToMCS(msg)
        Behavior.same
      }
      case msg: PublishEvent => {
        val positionDemands: Array[Byte] = messageTransformer.encodeEvent(msg.mcsPositionDemands)
        if (pubSocket.sendMore(EventConstants.MOUNT_DEMAND_POSITION)) {
          pubSocket.send(positionDemands)
        }
        Behavior.same
      }
      case msg: StartEventSubscription => {
        val eventName: String = subscribeSocket.recvStr()
        statePublisherActor ! PublishState(messageTransformer.decodeEvent(eventName, subscribeSocket.recv(ZMQ.DONTWAIT)))
        Behavior.same
      }
    }
  }

  private def submitCommandToMCS(msg: SubmitCommand) = {
    val controlCommand: ControlCommand = msg.controlCommand
    val commandName: String            = controlCommand.commandName.name
    log.info(msg = s"sending command : ${controlCommand.runId}, name : ${commandName} to MCS simulator")
    if (pushSocket.sendMore(commandName)) {
      if (pushSocket.send(messageTransformer.encodeMessage(controlCommand), ZMQ.NOBLOCK)) {
        msg.sender ! MCSResponse(readCommandResponse(commandName, controlCommand.runId))
      } else {
        msg.sender ! MCSResponse(
          CommandResponse.Error(controlCommand.runId, "Unable to submit command data to MCS subsystem.")
        )
      }
    } else {
      msg.sender ! MCSResponse(CommandResponse.Error(controlCommand.runId, "Unable to submit command data to MCS subsystem."))
    }
  }

  private def readCommandResponse(commandName: String, runId: Id): CommandResponse = {
    val responseCommandName: String = pullSocket.recvStr()
    if (commandName == responseCommandName) {
      if (pullSocket.hasReceiveMore) {
        log.info(s"Response for command :${commandName} is received and processing it")
        val responsePacket: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
        val response: SubystemResponse  = messageTransformer.decodeCommandResponse(responsePacket)
        paramSetTransformer.getCSWResponse(runId, response)
      }
    }
    CommandResponse.Invalid(runId, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
  }
  private def initMCSConnection(config: Config) = {
    val zeroMQPushSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPush")
    pushSocket.bind(zeroMQPushSocketStr)
    log.info(msg = s"ZeroMQ push socket is : ${zeroMQPushSocketStr} and pull socket is : ${zeroMQPushSocketStr}")

    val zeroMQPullSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPull")
    pullSocket.connect(zeroMQPullSocketStr)
    log.info(msg = s"ZeroMQ pull socket is : ${zeroMQPullSocketStr} and pull socket is : ${zeroMQPullSocketStr}")

    val zeroMQSubScribeSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQSub")
    log.info(msg = s"ZeroMQ sub scribe socket is : ${zeroMQSubScribeSocketStr} and pull socket is : ${zeroMQSubScribeSocketStr}")
    subscribeSocket.subscribe("Welcome to MCS Events".getBytes)
    subscribeSocket.connect(zeroMQSubScribeSocketStr)

    val zeroMQPubSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPub")
    pubSocket.bind(zeroMQPubSocketStr)
  }
}
