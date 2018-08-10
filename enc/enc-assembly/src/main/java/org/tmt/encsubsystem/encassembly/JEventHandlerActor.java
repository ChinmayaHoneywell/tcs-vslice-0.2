package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

/*** EventHandlerActor handles event processing.
 * it takes data from other actors like monitor actor and publish it using event service.
 * it also subscribes to events from other assemblies and provide events to other actors.
 */
public class JEventHandlerActor extends MutableBehavior<JEventHandlerActor.EventMessage> {


    // add messages here
    interface EventMessage {
    }

    public static final class EventPublishMessage implements EventMessage {
    }

    public static final class AssemblyStateChangeMessage implements EventMessage {

        public final JEncAssemblyHandlers.LifecycleState assemblyLifecycleState;
        public final JEncAssemblyHandlers.OperationalState assemblyOperationalState;

        public AssemblyStateChangeMessage(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
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
            return (MutableBehavior<EventMessage>) new JEventHandlerActor((ActorContext<JEventHandlerActor.EventMessage>) ctx, loggerFactory);
        });
    }

    /**
     * This method receives messages send to actor
     * and handle them based on their type.
     * @return
     */
    @Override
    public Behaviors.Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(EventPublishMessage.class,
                        command -> {
                            log.debug(() -> "EventPublishMessage Received");
                            publishEvent(command);
                            return Behaviors.same();
                        })
                .onMessage(AssemblyStateChangeMessage.class,
                        command -> {
                            log.debug(() -> "Changed assembly states Received");
                            publishAssemblyStates(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method publish received event object to csw event service
     * This functionality will be written once event service is released by csw team.
     * @param message
     */
    private void publishEvent(EventPublishMessage message) {

        log.debug(() -> "Publish Event Received ");
    }

    /**
     * This method publish received event object to csw event service
     *  This functionality will be written once event service is released by csw team.
     * @param message
     */
    private void publishAssemblyStates(AssemblyStateChangeMessage message) {

        log.info(() -> "Event Handler Actor , Lifecycle state - " + message.assemblyLifecycleState + ", Operation state - " + message.assemblyOperationalState);

        log.debug(() -> "States will be published on CSW event service from here");
    }


}
