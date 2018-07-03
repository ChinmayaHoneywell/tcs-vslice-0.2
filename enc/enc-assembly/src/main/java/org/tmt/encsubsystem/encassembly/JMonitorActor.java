package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.services.command.javadsl.JCommandService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;


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


    @Override
    public Behaviors.Receive<MonitorMessage> createReceive() {

        ReceiveBuilder<MonitorMessage> builder = receiveBuilder()
                .onMessage(AssemblyLifecycleStateChangeMessage.class,
                        message -> {
                            log.debug(() -> "AssemblyStateChangeMessage Received");
                            // change the behavior state
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
        JEncAssemblyHandlers.OperationalState derivedAssemblyOperationalState;

        if (message.hcdCommandService.isPresent()) {
            // HCD is connected, marking operational states of assembly idle.
            derivedAssemblyOperationalState = JEncAssemblyHandlers.OperationalState.Idle;
        } else {
            // as assembly is disconnected to hcd, monitor actor will not be receiving current states from hcd.
            // assembly is disconnected to hcd, then change state to disconnected/faulted
            derivedAssemblyOperationalState = JEncAssemblyHandlers.OperationalState.Faulted;
        }
        publishStateChange(assemblyLifecycleState, derivedAssemblyOperationalState);
        return JMonitorActor.behavior(assemblyLifecycleState, derivedAssemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);

    }

    private Behavior<MonitorMessage> onCurrentStateEventMessage(CurrentStateMessage message) {
        log.debug(() -> "current state handler  " + message.currentState);
        CurrentState currentState = message.currentState;

        switch (currentState.stateName().name()) {
            case "OpsAndLifecycleState":
                log.debug(() -> "HCD lifecycle,operational states received - ");
                JEncAssemblyHandlers.LifecycleState hcdLifecycleState = getLifecycleState(currentState);
                JEncAssemblyHandlers.OperationalState hcdOperationState = getOperationState(currentState);

                switch (hcdOperationState) {
                    case Following:
                        /* As control system is following demand positions, marking assembly state as slewing,
                         more appropriate state can be derived by comparing current position and demand position.
                         */
                        assemblyOperationalState = JEncAssemblyHandlers.OperationalState.Slewing;
                        assemblyLifecycleState = hcdLifecycleState;
                        break;
                    default:
                        assemblyOperationalState = hcdOperationState;
                        assemblyLifecycleState = hcdLifecycleState;
                }
                publishStateChange(assemblyLifecycleState, assemblyOperationalState);
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
            case "currentPosition":
                log.debug(() -> "Current position received - " + currentState);
                // by comparing demandPosition and current position here we can derive assembly state as slewing . tracking or in-position
                // As of now keeping state as is.
                publishStateChange(assemblyLifecycleState, assemblyOperationalState);
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
            default:
                return JMonitorActor.behavior(assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
        }

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
     * This method publish state changes to event handler actor.
     *
     * @param assemblyLifecycleState
     * @param assemblyOperationalState
     */
    private void publishStateChange(JEncAssemblyHandlers.LifecycleState assemblyLifecycleState, JEncAssemblyHandlers.OperationalState assemblyOperationalState) {
        log.debug(() -> "Assembly Monitor actor publishing states to actors - ");
        eventHandlerActor.tell(new JEventHandlerActor.AssemblyStateChangeMessage(assemblyLifecycleState, assemblyOperationalState));
    }


}
