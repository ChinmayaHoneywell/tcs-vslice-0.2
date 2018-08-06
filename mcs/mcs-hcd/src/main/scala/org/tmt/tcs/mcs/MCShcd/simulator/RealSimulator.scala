package org.tmt.tcs.mcs.MCShcd.simulator

import com.typesafe.config.Config
import csw.messages.commands.ControlCommand
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{IMessageTransformer, ProtoBuffMsgTransformer, SubscribedEvent, SubystemResponse}
import org.zeromq.ZMQ

object RealSimulator {
  def create(loggerFactory: LoggerFactory): Simulator = RealSimulator(loggerFactory)
}
case class RealSimulator(loggerFactory: LoggerFactory) extends Simulator {
  private val log                                     = loggerFactory.getLogger
  private val zmqContext: ZMQ.Context                 = ZMQ.context(1)
  private val pushSocket: ZMQ.Socket                  = zmqContext.socket(ZMQ.PUSH) //55579
  private val pullSocket: ZMQ.Socket                  = zmqContext.socket(ZMQ.PULL) //55578
  private val subscribeSocket: ZMQ.Socket             = zmqContext.socket(ZMQ.SUB) //55580
  private val addr: String                            = new String("tcp://localhost:")
  private val messageTransformer: IMessageTransformer = ProtoBuffMsgTransformer.create(loggerFactory)
  private var azPos: Double                           = 0.0
  private var elPos: Double                           = 0.0
  private var azPosError: Double                      = 0.0
  private var elPosError: Double                      = 0.0

  override def initializeSimulator(config: Config): Unit = {
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
  }
  override def publishCurrentPosition(): CurrentPosition = {
    log.info(msg = s"Receiving current position from MCS subsystem")
    val eventName: String = subscribeSocket.recvStr()
    if (eventName.equalsIgnoreCase("CurrentPosition")) {
      val subscribedEvent: SubscribedEvent = messageTransformer.decodeEvent("", subscribeSocket.recv(ZMQ.DONTWAIT))
      val mcsCurrPosition                  = subscribedEvent.mcsCurrentPosition
      azPos = mcsCurrPosition.getAzPos
      elPos = mcsCurrPosition.getElPos
      azPosError = mcsCurrPosition.getAzPosError
      elPosError = mcsCurrPosition.getElPosError
      CurrentPosition(mcsCurrPosition.getAzPos,
                      mcsCurrPosition.getElPos,
                      mcsCurrPosition.getAzPosError,
                      mcsCurrPosition.getElPosError)
    }
    CurrentPosition(azPos, elPos, azPosError, elPosError)
  }
  override def submitCommand(controlCommand: ControlCommand): Option[Boolean] = {
    val commandName: String = controlCommand.commandName.name
    log.info(msg = s"In Real simulator processing command : ${controlCommand.runId} and name : ${commandName}")
    val sendStatus: Boolean = pushSocket.sendMore(commandName)
    if (sendStatus) {
      pushSocket.send(messageTransformer.encodeMessage(controlCommand), ZMQ.NOBLOCK)

    }
    None
  }

  override def readCommandResponse(commandName: String): Option[SubystemResponse] = {
    val responseCommandName: String = pullSocket.recvStr()
    var responsePacket: Array[Byte] = null
    if (commandName == responseCommandName) {
      log.info(s"Response for command :${commandName} is received and processing it")
      if (pullSocket.hasReceiveMore) {
        responsePacket = pullSocket.recv(ZMQ.DONTWAIT)
        val response: SubystemResponse = messageTransformer.decode(responsePacket)
        Some(response)
      }
    }
    None
  }
}
