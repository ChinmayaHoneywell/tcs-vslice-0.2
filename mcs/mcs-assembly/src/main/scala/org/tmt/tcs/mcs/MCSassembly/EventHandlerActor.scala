package org.tmt.tcs.mcs.MCSassembly

import java.util.Calendar

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.framework.CurrentStatePublisher
import csw.messages.commands.ControlCommand
import csw.messages.events.{Event, SystemEvent}
import csw.services.command.scaladsl.CommandService
import csw.services.event.api.scaladsl.EventService
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.EventHandlerConstants
import org.tmt.tcs.mcs.MCSassembly.EventMessage._

import scala.concurrent.duration._
import org.tmt.tcs.mcs.MCSassembly.msgTransformer.EventTransformerHelper

import scala.concurrent.{Await, ExecutionContextExecutor, Future}

sealed trait EventMessage

object EventMessage {
  case class StartEventSubscription()                                extends EventMessage
  case class hcdLocationChanged(hcdLocation: Option[CommandService]) extends EventMessage
  case class PublishHCDState(event: Event)                           extends EventMessage
  case class StartPublishingDummyEvent()                             extends EventMessage

}

object EventHandlerActor {
  def createObject(eventService: EventService,
                   hcdLocation: Option[CommandService],
                   eventTransformer: EventTransformerHelper,
                   currentStatePublisher: CurrentStatePublisher,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        EventHandlerActor(
          ctx: ActorContext[EventMessage],
          eventService: EventService,
          hcdLocation: Option[CommandService],
          eventTransformer: EventTransformerHelper,
          currentStatePublisher: CurrentStatePublisher,
          loggerFactory: LoggerFactory
      )
    )
}
/*
This actor is responsible consuming incoming events to MCS Assembly and publishing outgoing
events from MCS Assembly using CSW EventService
 */
case class EventHandlerActor(ctx: ActorContext[EventMessage],
                             eventService: EventService,
                             hcdLocation: Option[CommandService],
                             eventTransformer: EventTransformerHelper,
                             currentStatePublisher: CurrentStatePublisher,
                             loggerFactory: LoggerFactory)
    extends MutableBehavior[EventMessage] {

  private val log                           = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val duration: Timeout            = 20 seconds
  private val eventSubscriber               = eventService.defaultSubscriber
  private val eventPublisher                = eventService.defaultPublisher

  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    msg match {
      case x: StartEventSubscription => subscribeEventMsg()
      case x: hcdLocationChanged => {
        // log.info(s"Changed HCD Location is : ${x.hcdLocation}")
        EventHandlerActor.createObject(eventService, x.hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
      }
      case x: PublishHCDState => publishReceivedEvent(x.event)

      case x: StartPublishingDummyEvent => {
        //publishDummyEventFromAssembly()
        //log.error(s"HCDLocation in publishDummyEvent is $hcdLocation")
        EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
      }

      case _ => {
        log.error(
          s"************************ Received unknown message  in EventHandlerActor ${msg} *********************"
        )
        EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
      }

    }
  }

  private def publishReceivedEvent(event: Event): Behavior[EventMessage] = {
    eventPublisher.publish(event, 40.seconds)
    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
  }
  /*
   *This function subscribes to position demand Events received from Other TCS Assemblies
   * using CSW EventService
   */
  private def subscribeEventMsg(): Behavior[EventMessage] = {
    log.info(msg = s"Started subscribing events Received from tpkAssembly.")
    eventSubscriber.subscribeCallback(EventHandlerConstants.PositionDemandKey, event => sendEventByAssemblyCurrentState(event))
    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
  }
  /*
    This function publishes position demands by using currentStatePublisher
   */
  private def sendEventByAssemblyCurrentState(msg: Event): Future[_] = {
    msg match {
      case systemEvent: SystemEvent => {
        //TODO : This time difference addition code is temporary it must removed once performance measurement is done
        val event = systemEvent.add(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY.set(System.currentTimeMillis()))
        // log.info(s"** Received position demands to mcs Assmebly at : ${event.get(EventHandlerConstants.TimeStampKey).get.head}, ${System.currentTimeMillis()}"   )
        val currentState = eventTransformer.getCurrentState(event)
        currentStatePublisher.publish(currentState)
        //log.info(s"Published demands current state : ${currentState}")
      }
      case _ => {
        log.error(s"Unable to map received position demands from tpk assembly to systemEvent: $msg")
      }
    }
    Future.successful("Successfully sent positionDemand by CurrentStatePublisher")
  }
  /*
  This function publishes event by using EventPublisher to the HCD
   */
  private def sendEventByEventPublisher(msg: Event): Future[_] = {

    log.info(s" *** Received positionDemand event: $msg to EventHandler at : ${System.currentTimeMillis()} *** ")

    msg match {
      case systemEvent: SystemEvent => {
        eventPublisher.publish(systemEvent)
      }
    }
    Future.successful("Successfully sent positionDemand by event publisher")
  }

  /*
    This function takes event input from EventSubscriber and if event is instance of
    SystemEvent it builds controlObject from systemEvent and sends this to HCD on commanService
    as a oneWayCommand.
   */
  private def sendEventByOneWayCommand(msg: Event, hcdLocation: Option[CommandService]): Future[_] = {

    log.info(
      s"*** Received positionDemand event: ${msg} to EventHandler OneWay ControlCommand at : ${System.currentTimeMillis()} ***"
    )

    msg match {
      case systemEvent: SystemEvent => {
        val controlCommand: ControlCommand = eventTransformer.getOneWayCommandObject(systemEvent)
        log.info(s"Transformed oneWay command object is $controlCommand")
        hcdLocation match {
          case Some(commandService) => {
            val response = Await.result(commandService.oneway(controlCommand), 5.seconds)
            log.info(
              s"*** Successfully submitted positionDemand Event : ${controlCommand} via oneWayCommand, " +
              s"response is : ${response} at time :${System.currentTimeMillis()} ***"
            )
            Future.successful(s"Successfully submitted positionDemand Event, response for the same is : ${msg}")
          }
          case None => {
            log.error("Unable to match hcdLocation to commandService")
            Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
          }
        }
      }
      case _ => {
        log.error(s"Unable to get systemEvent from incoming event")
        Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
      }

    }

  }
  private def publishDummyEventFromAssembly(): Unit = {

    log.info(msg = "Started publishing dummy Events from Assembly per 80 seconds")
    //new Thread(new Runnable { override def run(): Unit = sendDummyEvent }).start()

  }
  def sendDummyEvent() = {
    while (true) {
      Thread.sleep(80000)
      log.info(s"Publishing Dummy event from assembly current time is : ${Calendar.getInstance.getTime}")
      val event: SystemEvent = eventTransformer.getDummyAssemblyEvent()
      // eventPublisher.map(publisher => publisher.publish(event, 30.seconds))
      eventPublisher.publish(event, 80.seconds)
      log.info("Successfully published dummy event from assembly")
    }
  }
}
