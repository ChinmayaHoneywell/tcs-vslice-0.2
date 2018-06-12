package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.services.command.javadsl.JCommandService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;


public class JMonitorActor extends Behaviors.MutableBehavior<JMonitorActor.MonitorMessage> {


    // add messages here
    interface MonitorMessage {
    }

    public static final class AssemblyLifecycleStateChangeMessage implements MonitorMessage {

        public final JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState;

        public AssemblyLifecycleStateChangeMessage(JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState) {
            this.assemblyLifecycleState = assemblyLifecycleState;
        }
    }

    public static final class AssemblyOperationalStateChangeMessage implements MonitorMessage {

        public final JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState;

        public AssemblyOperationalStateChangeMessage(JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState) {
            this.assemblyOperationalState = assemblyOperationalState;
        }
    }

    public static final class LocationEventMessage implements MonitorMessage {

        public final Optional<JCommandService> hcdCommandService;

        public LocationEventMessage(Optional<JCommandService> hcdCommandService) {
            this.hcdCommandService = hcdCommandService;
        }
    }

    public static final class CurrentStateEventMessage implements MonitorMessage {

        public final CurrentState currentState;

        public CurrentStateEventMessage(CurrentState currentState) {
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

        public final JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState;
        public final JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState;

        public AssemblyStatesResponseMessage(JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState, JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState) {
            this.assemblyLifecycleState = assemblyLifecycleState;
            this.assemblyOperationalState = assemblyOperationalState;
        }
    }


    private ActorContext<MonitorMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;

    private JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState;
    private JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState;

    private JMonitorActor(ActorContext<MonitorMessage> actorContext, JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState, JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState, JLoggerFactory loggerFactory, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.assemblyLifecycleState = assemblyLifecycleState;
        this.assemblyOperationalState = assemblyOperationalState;

        this.eventHandlerActor = eventHandlerActor;
        this.commandHandlerActor = commandHandlerActor;
    }

    public static <MonitorMessage> Behavior<MonitorMessage> behavior(JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState, JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState, JLoggerFactory loggerFactory, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<MonitorMessage>) new JMonitorActor((ActorContext<JMonitorActor.MonitorMessage>) ctx, assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
        });
    }


    @Override
    public Behaviors.Receive<MonitorMessage> createReceive() {

        ReceiveBuilder<MonitorMessage> builder = receiveBuilder()
                .onMessage(AssemblyLifecycleStateChangeMessage.class,
                        message -> {
                            log.debug(()->"AssemblyStateChangeMessage Received");
                            // change the behavior state
                            return behavior(message.assemblyLifecycleState, assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);

                        })
                .onMessage(AssemblyOperationalStateChangeMessage.class,
                        message -> {
                            log.debug(()->"AssemblyMotionStateChangeMessage Received");
                            // change the behavior state
                            return behavior(assemblyLifecycleState, message.assemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
                        })
                .onMessage(LocationEventMessage.class,
                        message -> {
                            log.debug(()->"LocationEventMessage Received");
                            return onLocationEventMessage(message);
                        })
                .onMessage(CurrentStateEventMessage.class,
                        message -> {
                            log.debug(()->"CurrentStateEventMessage Received");
                            return onCurrentStateEventMessage(message);
                        })
                .onMessage(AssemblyStatesAskMessage.class,
                        message -> {
                            log.debug(()->"AssemblyStatesAskMessage Received");
                            // providing current lifecycle and operation state to sender for it's use such as validation.
                            message.replyTo.tell(new AssemblyStatesResponseMessage(assemblyLifecycleState, assemblyOperationalState));
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private Behavior<MonitorMessage> onLocationEventMessage(LocationEventMessage message) {
        JEncAssemblyHandlers.AssemblyLifecycleState derivedAssemblyLifecycleState;
        JEncAssemblyHandlers.AssemblyOperationalState derivedAssemblyOperationalState;

        if (message.hcdCommandService.isPresent()) {

            //TODO: Derive Assembly Lifecycle state and Operation state here based on current incoming states
            // if HCD is just came alive than states of assembly will be initialized and idle.
            derivedAssemblyLifecycleState = JEncAssemblyHandlers.AssemblyLifecycleState.Initialized;
            derivedAssemblyOperationalState = JEncAssemblyHandlers.AssemblyOperationalState.Idle;
        } else {
            // if hcdCommandService is empty, then change state to disconnected
            // as assembly is disconnected to hcd, monitor actor will not be receiving current states from hcd.
            derivedAssemblyLifecycleState = JEncAssemblyHandlers.AssemblyLifecycleState.Running;
            derivedAssemblyOperationalState = JEncAssemblyHandlers.AssemblyOperationalState.Faulted;
        }
        publishStateChangeToActors(derivedAssemblyLifecycleState, derivedAssemblyOperationalState);
        return JMonitorActor.behavior(derivedAssemblyLifecycleState, derivedAssemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);

    }

    private Behavior<MonitorMessage> onCurrentStateEventMessage(CurrentStateEventMessage message) {
        final JEncAssemblyHandlers.AssemblyLifecycleState derivedAssemblyLifecycleState ;
        final  JEncAssemblyHandlers.AssemblyOperationalState derivedAssemblyOperationalState ;
        log.debug(()->"current state handler");

        CurrentState currentState = message.currentState;

        log.debug(()->"current state = " + currentState);

        //TODO: Derive Assembly Lifecycle state and Operation state based on current states received from hcd
        // Monitor Actor can change its state depending on the current state of the HCD
        if ("tmt.tcs.ecs.currentPosition".equals(currentState.prefixStr())) {
            log.debug(()->"Current position received - " + currentState);

            // by comparing demandPosition and current position here we can derive assembly state as slewing . tracking or in-position
            // As of now keeping state as is.
            derivedAssemblyLifecycleState = assemblyLifecycleState;
            derivedAssemblyOperationalState = assemblyOperationalState;
        } else if ("tmt.tcs.ecs.currentLifecycleState".equals(currentState.prefixStr())) {
            log.debug(()->"Current states received - " + currentState);

            Parameter lifecycleStateParam = currentState.paramSet().find(x -> x.keyName().equals("LifecycleState")).get();
            String lifecycleStateString = (String) lifecycleStateParam.get(0).get();
            derivedAssemblyLifecycleState = JEncAssemblyHandlers.AssemblyLifecycleState.valueOf(lifecycleStateString);
            Parameter operationalStateParam = currentState.paramSet().find(x -> x.keyName().equals("OperationalState")).get();
            String operationalStateString = (String) operationalStateParam.get(0).get();
            derivedAssemblyOperationalState = JEncAssemblyHandlers.AssemblyOperationalState.valueOf(operationalStateString);
            log.debug(()->"Assembly states derived from HCD states - " + derivedAssemblyLifecycleState + " and " + derivedAssemblyOperationalState);
        }else {
            //no change in state
            derivedAssemblyLifecycleState = assemblyLifecycleState;
            derivedAssemblyOperationalState = assemblyOperationalState;
        }

        publishStateChangeToActors(derivedAssemblyLifecycleState, derivedAssemblyOperationalState);
        return JMonitorActor.behavior(derivedAssemblyLifecycleState, derivedAssemblyOperationalState, loggerFactory, eventHandlerActor, commandHandlerActor);
    }

    /**
     * This method publish state changes to event handler actor.
     *
     * @param assemblyLifecycleState
     * @param assemblyOperationalState
     */
    private void publishStateChangeToActors(JEncAssemblyHandlers.AssemblyLifecycleState assemblyLifecycleState, JEncAssemblyHandlers.AssemblyOperationalState assemblyOperationalState) {
        log.debug(()->"Assembly Monitor actor publishing states to actors - ");
        eventHandlerActor.tell(new JEventHandlerActor.AssemblyStateChangeMessage(assemblyLifecycleState, assemblyOperationalState));
    }


}
