package org.tmt.encsubsystem.encassembly;


import akka.actor.Cancellable;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.events.Event;
import csw.messages.events.EventName;
import csw.messages.events.SystemEvent;
import csw.messages.framework.ComponentInfo;
import csw.messages.params.generics.Parameter;
import csw.services.event.api.javadsl.IEventService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.time.Duration;
import java.time.Instant;

/*** EventHandlerActor handles event processing.
 * it takes data from other actors like monitor actor and publish event using event service.
 * it also subscribes to events from other assemblies and provide data to other actors.
 */
public class JEventHandlerActor extends MutableBehavior<JEventHandlerActor.EventMessage> {


    // add messages here
    interface EventMessage {
    }

    /**
     * Send this message to EventHandlerActor to start publishing assembly events to
     */
    public static final class AssemblyStateMessage implements EventMessage {
        public final JEncAssemblyHandlers.LifecycleState lifecycleState;
        public final JEncAssemblyHandlers.OperationalState operationalState;
        public final Instant time;

        public  AssemblyStateMessage(JEncAssemblyHandlers.LifecycleState lifecycleState, JEncAssemblyHandlers.OperationalState operationalState, Instant time){
            this.lifecycleState = lifecycleState;
            this.operationalState = operationalState;
            this.time = time;
        }

        @Override
        public String toString() {
            return "AssemblyStateMessage{" +
                    "lifecycleState=" + lifecycleState +
                    ", operationalState=" + operationalState +
                    ", time=" + time +
                    '}';
        }
    }

    /**
     * EventHandlerActor message for current position event.
     */
    public static final class CurrentPositionMessage implements  EventMessage{
        public final Parameter<Double> basePosParam;
        public final Parameter<Double> capPosParam;
        public final Parameter<Instant> subsystemTimestamp;
        public final Parameter<Instant> hcdTimestamp;
        public final Parameter<Instant> assemblyTimestamp;


        public CurrentPositionMessage(Parameter<Double> basePosParam, Parameter<Double> capPosParam, Parameter<Instant> subsystemTimestamp, Parameter<Instant> hcdTimestamp, Parameter<Instant> assemblyTimestamp) {
           this.basePosParam = basePosParam;
           this.capPosParam = capPosParam;
           this.subsystemTimestamp = subsystemTimestamp;
           this.hcdTimestamp = hcdTimestamp;
           this.assemblyTimestamp = assemblyTimestamp;
        }
    }

    public static final class HealthMessage implements  EventMessage {
        public final Parameter<String> healthParam;
        public final Parameter<String> healthReasonParam;
        public final Parameter<Instant> healthTimeParam;
        public final Parameter<Instant> assemblyTimestamp;

        public HealthMessage(Parameter<String> healthParam, Parameter<String> healthReasonParam, Parameter<Instant> healthTimeParam, Parameter<Instant> assemblyTimestamp) {
            this.healthParam = healthParam;
            this.healthReasonParam = healthReasonParam;
            this.healthTimeParam = healthTimeParam;
            this.assemblyTimestamp = assemblyTimestamp;
        }
    }

    /**
     * Upon receiving this message, EventHandlerActor will start publishing assembly state at defined frequency.
     */
    public static final class StartPublishingAssemblyState implements  EventMessage{

    }

    private ActorContext<EventMessage> actorContext;
    ComponentInfo componentInfo;
    IEventService eventService;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    /**
     * This hold latest assembly
     */
    private AssemblyStateMessage assemblyState;


    private JEventHandlerActor(ActorContext<EventMessage> actorContext, ComponentInfo componentInfo, IEventService eventService, JLoggerFactory loggerFactory,JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState ) {
        this.actorContext = actorContext;
        this.componentInfo = componentInfo;
        this.eventService = eventService;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());// how expensive is this operation?
        this.assemblyState = new AssemblyStateMessage(assemblyLifecycleState, assemblyOperationalState, Instant.now());

    }

    public static <EventMessage> Behavior<EventMessage> behavior(ComponentInfo componentInfo, IEventService eventService, JLoggerFactory loggerFactory, JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<EventMessage>) new JEventHandlerActor((ActorContext<JEventHandlerActor.EventMessage>) ctx, componentInfo, eventService, loggerFactory, assemblyLifecycleState, assemblyOperationalState);
        });
    }

    /**
     * This method receives messages sent to actor
     * and handle them based on their type.
     * @return
     */
    @Override
    public Behaviors.Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(CurrentPositionMessage.class,
                        currentPositionMessage -> {
                            log.debug(() -> "EventPublishMessage Received");
                            publishCurrentPosition(currentPositionMessage);
                            return Behaviors.same();
                        })
                .onMessage(AssemblyStateMessage.class,
                        assemblyStateMessage -> {
                            log.debug(() -> "Assembly state message received" + assemblyStateMessage);
                            this.assemblyState = assemblyStateMessage; // updating assembly state in event handler actor
                            return Behaviors.same();
                        })
                .onMessage(HealthMessage.class,
                        healthMessage -> {
                            log.debug(() -> "Health message received");
                            publishHealth(healthMessage);
                            return Behaviors.same();
                        })
                .onMessage(StartPublishingAssemblyState.class,
                assemblyStateMessage -> {
                    log.debug(() -> "Start Publishing Assembly State message received");
                    startPublishingAssemblyState();
                    return Behaviors.same();
                });
        return builder.build();
    }

    /**
     * This method publish current position event.
     * @param message
     */
    private void publishCurrentPosition(CurrentPositionMessage message) {
        SystemEvent currentPositionEvent = new SystemEvent(componentInfo.prefix(), new EventName(Constants.CURRENT_POSITION))
                .madd(message.basePosParam, message.capPosParam, message.subsystemTimestamp, message.hcdTimestamp, message.assemblyTimestamp);
        eventService.defaultPublisher().publish(currentPositionEvent);
    }

    /**
     * This method publish health event.
     * @param message
     */
    private void publishHealth(HealthMessage message) {
        SystemEvent healthEvent = new SystemEvent(componentInfo.prefix(), new EventName(Constants.HEALTH))
                .madd(message.healthParam, message.assemblyTimestamp, message.healthReasonParam, message.healthTimeParam);
        eventService.defaultPublisher().publish(healthEvent);
    }

    /**
     * This method create event generator to publish assembly state.
     * This method keep publishing latest assembly state over and over unless any update is received from monitor actor.
     * @return
     */
    private Cancellable startPublishingAssemblyState(){
        Event baseEvent = new SystemEvent(componentInfo.prefix(), new EventName(Constants.ASSEMBLY_STATE));
        return eventService.defaultPublisher().publish(()->
             ((SystemEvent) baseEvent)
                        .add(Constants.LIFECYCLE_KEY.set(this.assemblyState.lifecycleState.name()))
                        .add(Constants.OPERATIONAL_KEY.set(this.assemblyState.operationalState.name()))
                    .add(Constants.TIME_OF_STATE_DERIVATION.set(this.assemblyState.time))
        , Duration.ofMillis(1000/Constants.ASSEMBLY_STATE_EVENT_FREQUENCY_IN_HERTZ));
    }



}
