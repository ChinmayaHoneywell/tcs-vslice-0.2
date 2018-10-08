package org.tmt.tcs.mcs.MCSassembly

import java.util.Calendar
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.ControlCommand
import csw.messages.events.{Event, SystemEvent}
import csw.services.command.scaladsl.CommandService
import csw.services.event.api.scaladsl.{EventService, SubscriptionModes}
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
  case class DummyMsg()                                              extends EventMessage

}

object EventHandlerActor {
  def createObject(eventService: EventService,
                   hcdLocation: Option[CommandService],
                   eventTransformer: EventTransformerHelper,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        EventHandlerActor(
          ctx: ActorContext[EventMessage],
          eventService: EventService,
          hcdLocation: Option[CommandService],
          eventTransformer: EventTransformerHelper,
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
      case x: hcdLocationChanged     => EventHandlerActor.createObject(eventService, x.hcdLocation, eventTransformer, loggerFactory)
      case x: PublishHCDState        => publishReceivedEvent(x.event)

      case x: StartPublishingDummyEvent => {
        publishDummyEventFromAssembly()
        Behavior.same
      }

      case _ => {
        log.error(s"************************ Received unknown message  in EventHandlerActor*********************")
        Behavior.same
      }

    }
  }

  def publishReceivedEvent(event: Event): Behavior[EventMessage] = {
    eventPublisher.publish(event, 10.seconds, ex => log.info(s"${ex}"))
    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, loggerFactory)
  }
  /*
   *This function subscribes to position demand Events received from Other TCS Assemblies
   * using CSW EventService
   */
  def subscribeEventMsg(): Behavior[EventMessage] = {
    log.info(msg = s"Started subscribing events Received from ClientApp.")
    eventSubscriber.subscribeCallback(EventHandlerConstants.PositionDemandKey,
                                      event => sendEventByEventPublisher(event),
                                      10.seconds,
                                      SubscriptionModes.RateLimiterMode)

    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, loggerFactory)
  }
  /*
  This function publishes event by using EventPublisher to the HCD
   */
  private def sendEventByEventPublisher(msg: Event): Future[_] = {

    log.info(s" *** Received positionDemand event to EventHandler at : ${System.currentTimeMillis()} *** ")

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
  private def sendEventByOneWayCommand(msg: Event): Future[_] = {

    log.info(s"Sending event : ${msg} to HCD by oneWayCommand")
    msg match {
      case systemEvent: SystemEvent => {
        val controlCommand: ControlCommand = eventTransformer.getOneWayCommandObject(systemEvent)
        hcdLocation match {
          case Some(commandService: CommandService) => {
            import akka.util.Timeout
            val response = Await.result(commandService.oneway(controlCommand), 5.seconds)
            log.info(
              s"Successfully submitted positionDemand Event : ${controlCommand} through oneWayCommand, response is : ${response}"
            )
            Future.successful(s"Successfully submitted positionDemand Event, response for the same is : ${msg}")
          }
          case None => {
            Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
          }
        }
      }
      case _ => {
        Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
      }

    }

  }
  private def publishDummyEventFromAssembly(): Unit = {

    log.info(msg = "Started publishing dummy Events from Assembly per 30 seconds")
    new Thread(new Runnable { override def run(): Unit = sendDummyEvent }).start()

  }
  def sendDummyEvent() = {
    while (true) {
      Thread.sleep(60000)
      log.info(s"Publishing Dummy event from assembly current time is : ${Calendar.getInstance.getTime}")
      val event: SystemEvent = eventTransformer.getDummyAssemblyEvent()
      // eventPublisher.map(publisher => publisher.publish(event, 30.seconds))
      eventPublisher.publish(event, 30.seconds)
      log.info("Successfully published dummy event from assembly")
    }
  }
}
