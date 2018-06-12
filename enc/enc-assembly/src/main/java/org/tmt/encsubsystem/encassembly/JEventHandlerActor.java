package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;


public class JEventHandlerActor extends Behaviors.MutableBehavior<JEventHandlerActor.EventMessage> {


    // add messages here
    interface EventMessage {
    }

    public static final class EventPublishMessage implements EventMessage {
    }

    public static final class AssemblyStateChangeMessage implements EventMessage {

        public final JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState;
        public final JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState;

        public AssemblyStateChangeMessage(JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState, JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState) {
            this.assemblyLifecycleState = assemblyLifecycleState;
            this.assemblyOperationalState = assemblyOperationalState;
        }
    }


    private ActorContext<EventMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;


    private JEventHandlerActor(ActorContext<EventMessage> actorContext, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());


    }

    public static <EventMessage> Behavior<EventMessage> behavior(JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<EventMessage>) new JEventHandlerActor((ActorContext<JEventHandlerActor.EventMessage>) ctx, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(EventPublishMessage.class,
                        command -> {
                            log.debug("EventPublishMessage Received");
                            publishEvent(command);
                            return Behaviors.same();
                        })
                .onMessage(AssemblyStateChangeMessage.class,
                        command -> {
                            log.debug("Changed assembly states Received");
                            publishAssemblyStates(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void publishEvent(EventPublishMessage message) {

        log.debug("Publish Event Received ");
    }

    private void publishAssemblyStates(AssemblyStateChangeMessage message) {

        log.info("Event Handler Actor , Lifecycle state - " + message.assemblyLifecycleState + ", Operation state - " + message.assemblyOperationalState);

        log.debug("States will be published on CSW event service from here");
    }


}
