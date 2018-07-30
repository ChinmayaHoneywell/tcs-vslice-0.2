package org.tmt.tcs.mcs.MCShcd.simulator

import com.typesafe.config.Config
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{IMessageTransformer, ProtoBuffMsgTransformer, SubystemResponse}
import org.zeromq.ZMQ

object RealSimulator {
  def create(loggerFactory: LoggerFactory): Simulator = RealSimulator(loggerFactory)
}
case class RealSimulator(loggerFactory: LoggerFactory) extends Simulator {
  private val log                                     = loggerFactory.getLogger
  private val zmqContext: ZMQ.Context                 = ZMQ.context(1)
  private val pushSocket: ZMQ.Socket                  = zmqContext.socket(ZMQ.PUSH)
  private val pullSocket: ZMQ.Socket                  = zmqContext.socket(ZMQ.PULL)
  private val addr: String                            = new String("tcp://localhost:")
  private val messageTransformer: IMessageTransformer = ProtoBuffMsgTransformer.create(loggerFactory)

  override def initializeSimulator(config: Config): Unit = {
    val zeroMQPushSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPush")
    pushSocket.bind(zeroMQPushSocketStr)
    val zeroMQPullSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPull")
    pullSocket.connect(zeroMQPullSocketStr)
    log.info(msg = s"ZeroMQ push socket is : ${zeroMQPushSocketStr} and pull socket is : ${zeroMQPullSocketStr}")
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
    val responseCommandName: String = pullSocket.recvStr(ZMQ.DONTWAIT)
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
