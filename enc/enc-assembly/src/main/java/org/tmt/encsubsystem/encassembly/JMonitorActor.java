package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;

import csw.messages.params.states.CurrentState;
import csw.services.command.javadsl.JCommandService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;



public class JMonitorActor extends Behaviors.MutableBehavior<JMonitorActor.MonitorMessage> {


    public enum AssemblyState {
        Ready, Degraded, Disconnected, Faulted
    }
    public enum AssemblyMotionState {
        Idle, Slewing, Tracking, InPosition, Halted
    }

    // add messages here
    interface MonitorMessage {}

    public static final class AssemblyStateChangeMessage implements MonitorMessage {

        public final AssemblyState assemblyState;

        public AssemblyStateChangeMessage(AssemblyState assemblyState) {
            this.assemblyState = assemblyState;
        }
    }
    public static final class AssemblyMotionStateChangeMessage implements MonitorMessage {

        public final AssemblyMotionState assemblyMotionState;

        public AssemblyMotionStateChangeMessage(AssemblyMotionState assemblyMotionState) {
            this.assemblyMotionState = assemblyMotionState;
        }
    }

    public static final class LocationEventMessage implements MonitorMessage {

        public final Optional<JCommandService> templateHcd;

        public LocationEventMessage(Optional<JCommandService> templateHcd) {
            this.templateHcd = templateHcd;
        }
    }

    public static final class CurrentStateEventMessage implements MonitorMessage {

        public final CurrentState currentState;

        public CurrentStateEventMessage(CurrentState currentState) {
            this.currentState = currentState;
        }
    }


    private ActorContext<MonitorMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private AssemblyState assemblyState;
    private AssemblyMotionState assemblyMotionState;

    private JMonitorActor(ActorContext<MonitorMessage> actorContext, AssemblyState assemblyState, AssemblyMotionState assemblyMotionState, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.assemblyState = assemblyState;
        this.assemblyMotionState = assemblyMotionState;
    }

    public static <MonitorMessage> Behavior<MonitorMessage> behavior(AssemblyState assemblyState, AssemblyMotionState assemblyMotionState, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<MonitorMessage>) new JMonitorActor((ActorContext<JMonitorActor.MonitorMessage>) ctx, assemblyState, assemblyMotionState, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<MonitorMessage> createReceive() {

        ReceiveBuilder<MonitorMessage> builder = receiveBuilder()
                .onMessage(AssemblyStateChangeMessage.class,
                        message -> {
                            log.info("AssemblyStateChangeMessage Received");
                            // change the behavior state
                            return behavior(message.assemblyState, assemblyMotionState, loggerFactory);

                        })
                .onMessage(AssemblyMotionStateChangeMessage.class,
                        message -> {
                            log.info("AssemblyMotionStateChangeMessage Received");
                            // change the behavior state
                            return behavior(assemblyState, message.assemblyMotionState, loggerFactory);
                        })
                .onMessage(LocationEventMessage.class,
                        message -> {
                            log.info("LocationEventMessage Received");
                            return onLocationEventMessage(message);
                        })
                .onMessage(CurrentStateEventMessage.class,
                message -> {
                    log.info("CurrentStateEventMessage Received");
                    return onCurrentStateEventMessage(message);
                });
        return builder.build();
    }

    private Behavior<MonitorMessage> onLocationEventMessage(LocationEventMessage message) {

        if (message.templateHcd.isPresent() ) {

            if (assemblyState != AssemblyState.Disconnected) {
                // TODO: this logic is oversimplified: just because the state is no longer disconnected, does not mean it is Ready
                return JMonitorActor.behavior(AssemblyState.Ready, assemblyMotionState, loggerFactory);
            } else {
                return this;
            }
        } else {
            // if templateHcd is null, then change state to disconnected
            return JMonitorActor.behavior(AssemblyState.Disconnected, assemblyMotionState, loggerFactory);
        }
    }

    private Behavior<MonitorMessage> onCurrentStateEventMessage(CurrentStateEventMessage message) {

        log.info("current state handler");

        CurrentState currentState = message.currentState;

        log.info("current state = " + currentState);

        // here the Monitor Actor can change its state depending on the current state of the HCD
        return JMonitorActor.behavior(assemblyState, assemblyMotionState, loggerFactory);

    }


}
