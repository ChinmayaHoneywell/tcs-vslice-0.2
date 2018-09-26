package org.tmt.tcs.mcs.MCSassembly

import java.util.Calendar

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.messages.events.{Event, SystemEvent}
import csw.services.command.scaladsl.CommandService
import csw.services.event.api.scaladsl.{EventPublisher, EventService, EventSubscriber}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCSassembly.Constants.{Commands, EventConstants, EventHandlerConstants}
import org.tmt.tcs.mcs.MCSassembly.EventMessage._

import scala.concurrent.duration._
import org.tmt.tcs.mcs.MCSassembly.msgTransformer.EventTransformerHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

sealed trait EventMessage

object EventMessage {
  case class StartEventSubscription()                                extends EventMessage
  case class hcdLocationChanged(hcdLocation: Option[CommandService]) extends EventMessage
  case class PublishEvent(event: Event)                              extends EventMessage
  case class StartPublishingDummyEvent()                             extends EventMessage

}

object EventHandlerActor {
  def createObject(loggerFactory: LoggerFactory,
                   hcdLocation: Option[CommandService],
                   eventService: EventService): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        EventHandlerActor(ctx: ActorContext[EventMessage],
                          loggerFactory: LoggerFactory,
                          hcdLocation: Option[CommandService],
                          eventService: EventService)
    )
}
/*
This actor is responsible consuming incoming events to MCS Assembly and publishing outgoing
events from MCS Assembly using CSW EventService
 */
case class EventHandlerActor(ctx: ActorContext[EventMessage],
                             loggerFactory: LoggerFactory,
                             hcdLocation: Option[CommandService],
                             eventService: EventService)
    extends MutableBehavior[EventMessage] {

  private val log                                      = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor            = ctx.executionContext
  implicit val duration: Timeout                       = 20 seconds
  private val eventSubscriber: Future[EventSubscriber] = eventService.defaultSubscriber
  private val eventPublisher: Future[EventPublisher]   = eventService.defaultPublisher
  private val eventTransformer: EventTransformerHelper = EventTransformerHelper.create(loggerFactory)

  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    msg match {
      case x: StartEventSubscription => subscribeEventMsg()
      case x: hcdLocationChanged     => EventHandlerActor.createObject(loggerFactory, x.hcdLocation, eventService)
      case x: PublishEvent           => publishEvent(x.event)

      case x: StartPublishingDummyEvent => {
        publishDummyEventFromAssembly()
        Behavior.same
      }

    }
  }

  def publishEvent(event: Event): Behavior[EventMessage] = {
    log.info(msg = s"Received msg : ${event} for publishing")
    eventPublisher.map(publisher => publisher.publish(event))
    Behavior.same
  }
  /*
   *This function subscribes to position demand Events received from Other TCS Assemblies
   * using CSW EventService
   */
  def subscribeEventMsg(): Behavior[EventMessage] = {

    log.info(msg = s"Started subscribring events Received from Pointing Kernel.")
    eventSubscriber.map(
      subscriber => subscriber.subscribeAsync(EventHandlerConstants.PositionDemandKey, event => sendEventByOneWayCommand(event))
    )
    Behavior.same
  }
  /*
  This function publishes event by using EventPublisher to the HCD
   */
  private def sendEventByEventPublisher(msg: Event): Future[_] = {

    log.info(s"Sending event : ${msg} to HCD by evntPublisher")

    msg match {
      case systemEvent: SystemEvent => {
        //eventPublisher.map(pub => pub.publish(systemEvent))

        eventPublisher.onComplete {
          case Success(publisher: EventPublisher) => {
            log.info(
              s"Received Event : ${msg} in Assembly EventHandlerActor in sendEventByEventPublisher function publishing the same to HCD"
            )
            publisher.publish(systemEvent)
          }
          case Failure(e) => {
            log.error(s"Unable to get EventPublisher instance : ${e.printStackTrace()}")
            Future.failed(e)
          }
        }
      }
    }
    Future.successful("Successfully sent event by event publisher")
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
    while (true) {
      Thread.sleep(30000)
      println(
        s"Publishing Dummy event from assembly current time is" +
        s" : ${Calendar.getInstance.getTime}"
      )
      val event: SystemEvent = eventTransformer.getDummyAssemblyEvent()
      eventPublisher.map(publisher => publisher.publish(event, 30.seconds))
    }
  }

}
