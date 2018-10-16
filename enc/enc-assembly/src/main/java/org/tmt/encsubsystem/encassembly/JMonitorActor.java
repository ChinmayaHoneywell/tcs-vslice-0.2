package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.services.command.javadsl.JCommandService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static org.tmt.encsubsystem.encassembly.Constants.*;

/**
 * Monitor actor track hcd connection, events coming from hcd.
 * based on provided data it determined assembly state and health
 */
public class JMonitorActor extends MutableBehavior<JMonitorActor.MonitorMessage> {


    // add messages here
    interface MonitorMessage {
    }

    public static final class AssemblyLifecycleStateChangeMessage implements MonitorMessage {

        public final JEncAssemblyHandlers.LifecycleState assemblyLifecycleState;

        public AssemblyLifecycleStateChangeMessage(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState) {
            this.assemblyLifecycleState = assemblyLifecycleState;
        }
    }

    public static final class AssemblyOperationalStateChangeMessage implements MonitorMessage {

        public final JEncAssemblyHandlers.OperationalState assemblyOperationalState;

        public AssemblyOperationalStateChangeMessage(JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
            this.assemblyOperationalState = assemblyOperationalState;
        }
    }

    public static final class LocationEventMessage implements MonitorMessage {

        public final Optional<JCommandService> hcdCommandService;

        public LocationEventMessage(Optional<JCommandService> hcdCommandService) {
            this.hcdCommandService = hcdCommandService;
        }
    }

    public static final class CurrentStateMessage implements MonitorMessage {

        public final CurrentState currentState;

        public CurrentStateMessage(CurrentState currentState) {
            this.currentState = currentState;
        }
    }


    public static final class AssemblyStatesAskMessage implements MonitorMessage {

        public final ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo;

        public AssemblyStatesAskMessage(ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AssemblyStatesResponseMessage implements MonitorMessage {

        public final JEncAssemblyHandlers.LifecycleState assemblyLifecycleState;
        public final JEncAssemblyHandlers.OperationalState assemblyOperationalState;

        public AssemblyStatesResponseMessage(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
            this.assemblyLifecycleState = assemblyLifecycleState;
            this.assemblyOperationalState = assemblyOperationalState;
        }
    }


    private ActorContext<MonitorMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;

    private JEncAssemblyHandlers.LifecycleState assemblyLifecycleState;
    private JEncAssemblyHandlers.OperationalState assemblyOperationalState;

    Key<Double> basePosKey = JKeyTypes.DoubleKey().make("basePosKey");
    Key<Double> capPosKey = JKeyTypes.DoubleKey().make("capPosKey");


    private JMonitorActor(ActorContext<MonitorMessage> actorContext, JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState, JLoggerFactory loggerFactory, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.assemblyLifecycleState = assemblyLifecycleState;
        this.assemblyOperationalState = assemblyOperationalState;

        this.eventHandlerActor = eventHandlerActor;
        this.commandHandlerActor = commandHandlerActor;
    }

    public static <MonitorMessage> Behavior<MonitorMessage> behavior(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState, JLoggerFactory loggerFactory, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<MonitorMessage>) new JMonitorActor((ActorContext<JMonitorActor.MonitorMessage>) ctx, assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Behaviors.Receive<MonitorMessage> createReceive() {

        ReceiveBuilder<MonitorMessage> builder = receiveBuilder()
                .onMessage(AssemblyLifecycleStateChangeMessage.class,
                        message -> {
                            log.debug(() -> "AssemblyStateChangeMessage Received");
                            // change the lifecycle state
                            return behavior(message.assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);

                        })
                .onMessage(AssemblyOperationalStateChangeMessage.class,
                        message -> {
                            log.debug(() -> "AssemblyMotionStateChangeMessage Received");
                            // change the behavior state
                            return behavior(assemblyLifecycleState, message.assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
                        })
                .onMessage(LocationEventMessage.class,
                        message -> {
                            log.debug(() -> "LocationEventMessage Received");
                            return onLocationEventMessage(message);
                        })
                .onMessage(CurrentStateMessage.class,
                        message -> {
                            log.debug(() -> "CurrentStateEventMessage Received");
                            return onCurrentStateEventMessage(message);
                        })
                .onMessage(AssemblyStatesAskMessage.class,
                        message -> {
                            log.debug(() -> "AssemblyStatesAskMessage Received");
                            // providing current lifecycle and operation state to sender for it's use such as validation.
                            message.replyTo.tell(new AssemblyStatesResponseMessage(assemblyLifecycleState, assemblyOperationalState));
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method derives assembly operational state based on connectivity to hcd.
     *
     * @param message
     * @return
     */
    private Behavior<MonitorMessage> onLocationEventMessage(LocationEventMessage message) {
        if (message.hcdCommandService.isPresent()) {
            // HCD is connected, marking operational states of assembly idle.
            assemblyOperationalState = JEncAssemblyHandlers.OperationalState.Idle;
        } else {
            // as assembly is disconnected to hcd, monitor actor will not be receiving current states from hcd.
            // assembly is disconnected to hcd, then change state to disconnected/faulted
            assemblyOperationalState = JEncAssemblyHandlers.OperationalState.Faulted;
        }
        forwardAssemblyStateToEventHandlerActor(assemblyLifecycleState, assemblyOperationalState);
        return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);

    }

    private Behavior<MonitorMessage> onCurrentStateEventMessage(CurrentStateMessage message) {
        CurrentState currentState = message.currentState;
        switch (currentState.stateName().name()) {
            case HCD_STATE:
                log.debug(() -> "HCD lifecycle,operational states received - ");
                JEncAssemblyHandlers.LifecycleState hcdLifecycleState = getLifecycleState(currentState);
                JEncAssemblyHandlers.OperationalState hcdOperationState = getOperationState(currentState);

                switch (hcdOperationState) {
                    case Following:
                        /* As control system is following demand positions, marking assembly state as slewing,
                         more appropriate state will be derived by comparing current position and demand position.
                         */
                        assemblyOperationalState = JEncAssemblyHandlers.OperationalState.Slewing;
                        assemblyLifecycleState = hcdLifecycleState;
                        break;
                    default:
                        assemblyOperationalState = hcdOperationState;
                        assemblyLifecycleState = hcdLifecycleState;
                }
                forwardAssemblyStateToEventHandlerActor(assemblyLifecycleState, assemblyOperationalState);
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
            case CURRENT_POSITION:
                log.debug(() -> "Current position received - " + currentState);
                //.get(azPosKey).get().value(0)
                /* by comparing demandPosition and current position here we can derive assembly state as slewing . tracking or in-position
                 As of now keeping state as is. it is conditional to update assembly state
                 like in case neither follow nor move command is in progress then no need
                 to determine slewing, tracking or in position state.
                */
                forwardAssemblyStateToEventHandlerActor(assemblyLifecycleState, assemblyOperationalState);
                forwardCurrentPositionToEventHandlerActor(getCurrentPosition(currentState));
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
            case HEALTH:
                log.debug(() -> "Health received from HCD- " + currentState);
                forwardHealthToEventHandlerActor(getHealth(currentState));
                Behaviors.same();
            default:
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
        }

    }

    /**
     * Extracting current position parameters from current state into current position message for EventHandlerActor
     * & Adding assembly processing timestamp to current position.
     * @param currentState
     * @return
     */
    private JEventHandlerActor.CurrentPositionMessage getCurrentPosition(CurrentState currentState) {
        Parameter<Double> basePosParam  = currentState.jGet(BASE_POS_KEY).get();
        Parameter<Double> capPosParam  = currentState.jGet(CAP_POS_KEY).get();
        Parameter<Instant> subsystemTimestampKey  = currentState.jGet(SUBSYSTEM_TIMESTAMP_KEY).get();
        Parameter<Instant> hcdTimestampKey  = currentState.jGet(HCD_TIMESTAMP_KEY).get();
        Parameter<Instant> assemblyTimestampKey  = ASSEMBLY_TIMESTAMP_KEY.set(Instant.now());
        return new JEventHandlerActor.CurrentPositionMessage(basePosParam,capPosParam,subsystemTimestampKey, hcdTimestampKey, assemblyTimestampKey);
    }

    /**
     * Extracting health parameters from current state into health message for EventHandlerActor
     * & Adding assembly processing timestamp to current position.
     * @param currentState
     * @return
     */
    private JEventHandlerActor.HealthMessage getHealth(CurrentState currentState) {
        Parameter<String> healthParam  = currentState.jGet(HEALTH_KEY).get();
        Parameter<String> healthReasonParam = currentState.jGet(HEALTH_REASON_KEY).get();
        Parameter<Instant> healthTimeParam = currentState.jGet(HEALTH_TIME_KEY).get();
        Parameter<Instant> assemblyTimestampKey  = ASSEMBLY_TIMESTAMP_KEY.set(Instant.now());
        return new JEventHandlerActor.HealthMessage(healthParam, healthReasonParam, healthTimeParam, assemblyTimestampKey);
    }

    /**
     * Converting Parameter based representation to enum based representation
     *
     * @param currentState
     * @return
     */
    private JEncAssemblyHandlers.OperationalState getOperationState(CurrentState currentState) {
        Parameter operationalStateParam = currentState.paramSet().find(x -> x.keyName().equals("OperationalState")).get();
        String operationalStateString = (String) operationalStateParam.get(0).get();
        return JEncAssemblyHandlers.OperationalState.valueOf(operationalStateString);
    }

    /**
     * Converting Parameter based representation to enum based representation
     *
     * @param currentState
     * @return
     */
    private JEncAssemblyHandlers.LifecycleState getLifecycleState(CurrentState currentState) {
        Parameter lifecycleStateParam = currentState.paramSet().find(x -> x.keyName().equals("LifecycleState")).get();
        String lifecycleStateString = (String) lifecycleStateParam.get(0).get();
        return JEncAssemblyHandlers.LifecycleState.valueOf(lifecycleStateString);
    }

    /**
     * This method forwards assembly state to EventHandlerActor for publishing it as event.
     * This method does not decide frequency of publishing assembly state event.
     * @param assemblyLifecycleState
     * @param assemblyOperationalState
    */
    private void forwardAssemblyStateToEventHandlerActor(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
        log.debug(() -> "Assembly Monitor actor publishing states to actors - ");
        eventHandlerActor.tell(new JEventHandlerActor.AssemblyStateMessage(assemblyLifecycleState, assemblyOperationalState, Instant.now()));
    }
    /**
     * This method forwards current position to EventHandlerActor for publishing it as event.
     *
     */
    private void forwardCurrentPositionToEventHandlerActor(JEventHandlerActor.CurrentPositionMessage currentPositionMessage) {
        eventHandlerActor.tell(currentPositionMessage);
    }
    /**
     * This method forwards health to EventHandlerActor for publishing it as event.
     *
     */
    private void forwardHealthToEventHandlerActor(JEventHandlerActor.HealthMessage healthMessage) {
        eventHandlerActor.tell(healthMessage);
    }

}
