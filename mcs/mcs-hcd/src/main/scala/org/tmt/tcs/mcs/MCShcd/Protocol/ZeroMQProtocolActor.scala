package org.tmt.tcs.mcs.MCShcd.Protocol

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import com.typesafe.config.Config
import csw.messages.commands.{CommandIssue, CommandResponse, ControlCommand}
import csw.messages.events.SystemEvent
import csw.messages.params.models.Id
import csw.messages.params.states.CurrentState
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.tmt.tcs.mcs.MCShcd.EventMessage
import org.tmt.tcs.mcs.MCShcd.EventMessage.PublishState
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage._
import org.tmt.tcs.mcs.MCShcd.constants.EventConstants
import org.tmt.tcs.mcs.MCShcd.msgTransformers._
import org.zeromq.ZMQ
sealed trait ZeroMQMessage
object ZeroMQMessage {

  case class InitializeSimulator(sender: ActorRef[ZeroMQMessage], config: Config) extends ZeroMQMessage

  case class SubmitCommand(sender: ActorRef[ZeroMQMessage], controlCommand: ControlCommand) extends ZeroMQMessage
  case class MCSResponse(commandResponse: CommandResponse)                                  extends ZeroMQMessage
  case class PublishEvent(event: SystemEvent)                                               extends ZeroMQMessage
  case class StartSimulEventSubscr()                                                        extends ZeroMQMessage

  case class SimulatorConnResponse(connected: Boolean)            extends ZeroMQMessage
  case class Disconnect()                                         extends ZeroMQMessage
  case class PublishCurrStateToZeroMQ(currentState: CurrentState) extends ZeroMQMessage

}
object ZeroMQProtocolActor {
  def create(statePublisherActor: ActorRef[EventMessage], loggerFactory: LoggerFactory): Behavior[ZeroMQMessage] =
    Behaviors.setup(ctx => ZeroMQProtocolActor(ctx, statePublisherActor, loggerFactory))
}
case class ZeroMQProtocolActor(ctx: ActorContext[ZeroMQMessage],
                               statePublisherActor: ActorRef[EventMessage],
                               loggerFactory: LoggerFactory)
    extends MutableBehavior[ZeroMQMessage] {
  private val log: Logger                 = loggerFactory.getLogger
  private val zmqContext: ZMQ.Context     = ZMQ.context(1)
  private val pushSocket: ZMQ.Socket      = zmqContext.socket(ZMQ.PUSH) //55579
  private val pullSocket: ZMQ.Socket      = zmqContext.socket(ZMQ.PULL) //55578
  private val pubSocket: ZMQ.Socket       = zmqContext.socket(ZMQ.PUB) //55581
  private val subscribeSocket: ZMQ.Socket = zmqContext.socket(ZMQ.SUB) //55580

  private val addr: String                             = new String("tcp://localhost:")
  private val messageTransformer: IMessageTransformer  = ProtoBuffMsgTransformer.create(loggerFactory)
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  private var commandSendTime: Long                    = 0
  private var commandResponseTime: Long                = 0
  private var commandEncodeTime: Long                  = 0
  private var commandDecodeTime: Long                  = 0
  private var zeroMQPullSocketStr: String              = ""
  private var zeroMQPushSocketStr: String              = ""
  private var zeroMQSubScribeSocketStr: String         = ""
  private var zeroMQPubSocketStr: String               = ""
  /*
  1. PublishEvent is used when positionDemand is propagated from Assembly to HCD using CSW EventService.
  2. PublishCurrStateToZeroMQ is used when positionDemand is propagated from Assembly to HCD using CSW CurrentState.
   */
  override def onMessage(msg: ZeroMQMessage): Behavior[ZeroMQMessage] = {
    msg match {

      case msg: InitializeSimulator => {
        if (initMCSConnection(msg.config)) {
          log.info("CONNECTION ESTABLISHED WITH MCS SIMULATOR")
          msg.sender ! SimulatorConnResponse(true)
        } else {
          log.error("UNABLE TO MAKE CONNECTION WITH MCS SIMULATOR")
          msg.sender ! SimulatorConnResponse(false)
        }
        Behavior.same
      }
      case msg: SubmitCommand => {
        submitCommandToMCS(msg)
        Behavior.same
      }
      case msg: PublishEvent => {
        val positionDemands: Array[Byte] = messageTransformer.encodeEvent(msg.event)
        if (pubSocket.sendMore(EventConstants.MOUNT_DEMAND_POSITION)) {
          pubSocket.send(positionDemands)
        }

        Behavior.same
      }
      case msg: PublishCurrStateToZeroMQ => {
        try {
          val positionDemands: Array[Byte] = messageTransformer.encodeCurrentState(msg.currentState)
          if (pubSocket.sendMore(EventConstants.MOUNT_DEMAND_POSITION)) {
            pubSocket.send(positionDemands)
          }
          //log.info(s"published position demands : ${msg.currentState} to MCS subsystem")
        } catch {
          case ex: Exception => {
            ex.printStackTrace()
            log.error("Exception in converting current state to byte array")
          }
        }

        Behavior.same
      }
      case msg: StartSimulEventSubscr => {
        new Thread(new Runnable {
          override def run(): Unit = startSubscrToSimulEvents()
        }).start()
        log.info("Started subscribing to events from Simulator.")
        Behavior.same
      }
      case msg: Disconnect => {
        disconnectFromMCS()
        Behavior.same
      }
    }
  }
  private def startSubscrToSimulEvents() = {
    while (true) {
      val eventName: String = subscribeSocket.recvStr()
      //log.info(s"Received event: ${eventName} from Simulator")
      if (subscribeSocket.hasReceiveMore) {
        val eventData       = subscribeSocket.recv(ZMQ.DONTWAIT)
        val hcdReceivalTime = System.currentTimeMillis()
        val currentState    = messageTransformer.decodeEvent(eventName, eventData)
        val currState       = currentState.add(EventConstants.hcdEventReceivalTime_Key.set(hcdReceivalTime))
        // log.info(s"Publishing event: $currState from HCD.")
        statePublisherActor ! PublishState(currState)
      } else {
        log.error(s"No event data is received for event: ${eventName}")
      }
    }
  }
  private def submitCommandToMCS(msg: SubmitCommand) = {
    val controlCommand: ControlCommand = msg.controlCommand
    val commandName: String            = controlCommand.commandName.name
    val commandNameSent                = pushSocket.sendMore(commandName)
    if (commandNameSent) {
      commandEncodeTime = System.currentTimeMillis()
      val encodedCommand = messageTransformer.encodeMessage(controlCommand)
      // log.info(s" ** Time required to encode command: ${commandName} is: ${System.currentTimeMillis() - commandEncodeTime} ** ")
      commandSendTime = System.currentTimeMillis()
      if (pushSocket.send(encodedCommand, ZMQ.NOBLOCK)) {
        // log.info(s" ** Time required to send command: ${commandName} is: ${System.currentTimeMillis() - commandSendTime} ** ")
        msg.sender ! MCSResponse(readCommandResponse(commandName, controlCommand.runId))
      } else {
        //log.info(s" ** Time required to send command: ${commandName} is: ${System.currentTimeMillis() - commandSendTime} ** ")
        msg.sender ! MCSResponse(CommandResponse.Error(controlCommand.runId, "Unable to submit command data to MCS subsystem."))
      }
    } else {
      msg.sender ! MCSResponse(CommandResponse.Error(controlCommand.runId, "Unable to submit command data to MCS subsystem."))
    }
  }

  private def readCommandResponse(commandName: String, runId: Id): CommandResponse = {
    val responseCommandName: String = pullSocket.recvStr()
    if (commandName == responseCommandName) {
      if (pullSocket.hasReceiveMore) {
        val responsePacket: Array[Byte] = pullSocket.recv(ZMQ.DONTWAIT)
        /*log.info(
          s" ** Time required to get response for command: ${commandName} is: ${System.currentTimeMillis() - commandSendTime} ** "
        )*/
        commandDecodeTime = System.currentTimeMillis()
        val response: SubystemResponse = messageTransformer.decodeCommandResponse(responsePacket)
        val commandResponse            = paramSetTransformer.getCSWResponse(runId, response)
        /* log.info(
          s"Time required for decoding response for command: ${commandName} is: ${System.currentTimeMillis() - commandDecodeTime}"
        )*/
        commandResponse
      } else {
        CommandResponse.Invalid(runId, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
      }
    } else {
      CommandResponse.Invalid(runId, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
    }

  }

  private def initMCSConnection(config: Config): Boolean = {
    log.info(s"config object is :${config}")
    zeroMQPushSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPush")
    val pushSocketConn = pushSocket.bind(zeroMQPushSocketStr)
    log.info(msg = s"ZeroMQ push socket is : ${zeroMQPushSocketStr} and connection : ${pushSocketConn}")

    zeroMQPullSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPull")
    val pullSocketConn = pullSocket.connect(zeroMQPullSocketStr)
    log.info(msg = s"ZeroMQ pull socket is : ${zeroMQPullSocketStr} and connection : ${pullSocketConn}")

    zeroMQSubScribeSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQSub")
    //subscribeSocket.subscribe("Welcome to MCS Events".getBytes)
    val subSockConn = subscribeSocket.connect(zeroMQSubScribeSocketStr)
    subscribeSocket.subscribe(ZMQ.SUBSCRIPTION_ALL) // added this becz unable to receive msgs without this.
    log.info(msg = s"ZeroMQ subscribe socket is : ${zeroMQSubScribeSocketStr} and connection is : ${subSockConn}")

    zeroMQPubSocketStr = addr + config.getInt("tmt.tcs.mcs.zeroMQPub")
    val pubSockConn = pubSocket.bind(zeroMQPubSocketStr)
    log.info(msg = s"ZeroMQ pub socket is : ${zeroMQPubSocketStr} and connection is : ${pubSockConn}")

    pushSocketConn && pullSocketConn && subSockConn && pubSockConn
  }
  private def disconnectFromMCS(): Unit = {
    pushSocket.disconnect(zeroMQPushSocketStr)
    pushSocket.close()
    pullSocket.disconnect(zeroMQPullSocketStr)
    pullSocket.close()
    subscribeSocket.disconnect(zeroMQSubScribeSocketStr)
    subscribeSocket.close()
    pubSocket.disconnect(zeroMQPubSocketStr)
    pubSocket.close()
  }
}
