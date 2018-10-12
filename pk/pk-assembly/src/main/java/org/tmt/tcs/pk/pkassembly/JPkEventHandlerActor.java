package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JPkEventHandlerActor extends MutableBehavior<JPkEventHandlerActor.EventMessage> {


    // add messages here
    interface EventMessage {}

    public static final class EventPublishMessage implements EventMessage { }


    private ActorContext<EventMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;


    private JPkEventHandlerActor(ActorContext<EventMessage> actorContext, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

    }

    public static <EventMessage> Behavior<EventMessage> behavior(JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<EventMessage>) new JPkEventHandlerActor((ActorContext<JPkEventHandlerActor.EventMessage>) ctx, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(EventPublishMessage.class,
                        command -> {
                            log.info("EventPublishMessage Received");
                            publishEvent(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void publishEvent(EventPublishMessage message) {

        log.info("Publish Event Received ");
    }


}
