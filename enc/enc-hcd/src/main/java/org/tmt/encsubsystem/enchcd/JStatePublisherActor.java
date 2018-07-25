package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.javadsl.TimerScheduler;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.messages.params.states.StateName;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static csw.messages.javadsl.JUnits.degree;


public class JStatePublisherActor extends MutableBehavior<JStatePublisherActor.StatePublisherMessage> {


    // add messages here
    interface StatePublisherMessage {
    }

    public static final class StartMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StartMessage)) {
                return false;
            }
            return true;

        }
    }

    public static final class StopMessage implements StatePublisherMessage {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StopMessage)) {
                return false;
            }
            return true;

        }
    }

    public static final class PublishMessage implements StatePublisherMessage {
    }

    /**
     * This message is used to change state of HCD
     * Either Lifecycle state or Operational state can be changed or both.
     */
    public static final class StateChangeMessage implements StatePublisherMessage {

        public final Optional<JEncHcdHandlers.LifecycleState> lifecycleState;
        public final Optional<JEncHcdHandlers.OperationalState> operationalState;

        public StateChangeMessage(Optional<JEncHcdHandlers.LifecycleState> lifecycleState, Optional<JEncHcdHandlers.OperationalState> operationalState) {
            this.lifecycleState = lifecycleState;
            this.operationalState = operationalState;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StateChangeMessage)) {
                return false;
            }
            boolean isSame = this.lifecycleState.equals(((StateChangeMessage) obj).lifecycleState) && this.operationalState.equals(((StateChangeMessage) obj).operationalState);
            return isSame;
        }
    }

    private JLoggerFactory loggerFactory;
    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;

    JEncHcdHandlers.LifecycleState lifecycleState;
    JEncHcdHandlers.OperationalState operationalState;


    String currentStatePrefix = "tmt.tcs.ecs.currentStates";

    // current state name and Object to represent lifecycle state and operational state of hcd/subsystem
    String OpsAndLifecycleStateName = "OpsAndLifecycleState";
    CurrentState OpsAndLyfCycleCurrentState;
    //Keys to represent lifecycle state and operational state, parameters will be created from these keys
    Key lifecycleKey = JKeyTypes.StringKey().make("LifecycleState");
    Key operationalkey = JKeyTypes.StringKey().make("OperationalState");


    // state name for current position
    String currentPositionStateName = "currentPosition";

    //keys for creating current position parameters
    Key timestampKey = JKeyTypes.TimestampKey().make("timestampKey");

    Key azPosKey = JKeyTypes.DoubleKey().make("azPosKey");
    Key azPosErrorKey = JKeyTypes.DoubleKey().make("azPosErrorKey");
    Key elPosKey = JKeyTypes.DoubleKey().make("elPosKey");
    Key elPosErrorKey = JKeyTypes.DoubleKey().make("elPosErrorKey");
    Key azInPositionKey = JKeyTypes.BooleanKey().make("azInPositionKey");
    Key elInPositionKey = JKeyTypes.BooleanKey().make("elInPositionKey");

    private static final Object TIMER_KEY = new Object();

    private JStatePublisherActor(TimerScheduler<StatePublisherMessage> timer, CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory, JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
        this.timer = timer;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(this.getClass());
        this.currentStatePublisher = currentStatePublisher;
        this.lifecycleState = lifecycleState;
        this.operationalState = operationalState;

        OpsAndLyfCycleCurrentState = new CurrentState(currentStatePrefix, new StateName(OpsAndLifecycleStateName));
    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory, JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
        return Behaviors.withTimers(timers -> {
            return (MutableBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, currentStatePublisher, loggerFactory, lifecycleState, operationalState);
        });
    }


    @Override
    public Behaviors.Receive<StatePublisherMessage> createReceive() {

        ReceiveBuilder<StatePublisherMessage> builder = receiveBuilder()
                .onMessage(StartMessage.class,
                        command -> {
                            log.debug(() -> "StartMessage Received");
                            onStart(command);
                            return Behaviors.same();
                        })
                .onMessage(StopMessage.class,
                        command -> {
                            log.debug(() -> "StopMessage Received");
                            onStop(command);
                            return Behaviors.same();
                        })
                .onMessage(PublishMessage.class,
                        command -> {
                            log.debug(() -> "PublishMessage Received");
                            onPublishMessage(command);
                            return Behaviors.same();
                        })
                .onMessage(StateChangeMessage.class,
                        command -> {
                            log.debug(() -> "LifecycleStateChangeMessage Received");
                            handleStateChange(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void onStart(StartMessage message) {

        log.debug(() -> "Start Message Received ");

        timer.startPeriodicTimer(TIMER_KEY, new PublishMessage(), Duration.ofSeconds(60));

        log.debug(() -> "start message completed");


    }

    private void onStop(StopMessage message) {

        log.debug(() -> "Stop Message Received ");
        timer.cancel(TIMER_KEY);
    }

    /**
     * This method update state of hcd. this changed state is published to assembly.
     *
     * @param message
     */
    private void handleStateChange(StateChangeMessage message) {
        //change current state or if state is not present in message then keep it as is.
        lifecycleState = message.lifecycleState.orElse(lifecycleState);
        operationalState = message.operationalState.orElse(operationalState);
        CurrentState currentState = OpsAndLyfCycleCurrentState
                .add(lifecycleKey.set(lifecycleState.name()))
                .add(operationalkey.set(operationalState.name()));

        currentStatePublisher.publish(currentState);
    }

    private void onPublishMessage(PublishMessage message) {

        log.debug(() -> "Publish Message Received ");

        // example parameters for a current state

        Parameter azPosParam = azPosKey.set(35.34).withUnits(degree);
        Parameter azPosErrorParam = azPosErrorKey.set(0.34).withUnits(degree);
        Parameter elPosParam = elPosKey.set(46.7).withUnits(degree);
        Parameter elPosErrorParam = elPosErrorKey.set(0.03).withUnits(degree);
        Parameter azInPositionParam = azInPositionKey.set(false);
        Parameter elInPositionParam = elInPositionKey.set(true);

        Parameter timestamp = timestampKey.set(Instant.now());

        //create CurrentState and use sequential add
        CurrentState currentPosition = new CurrentState(currentStatePrefix, new StateName(currentPositionStateName))
                .add(azPosParam)
                .add(elPosParam)
                .add(azPosErrorParam)
                .add(elPosErrorParam)
                .add(azInPositionParam)
                .add(elInPositionParam)
                .add(timestamp);

        currentStatePublisher.publish(currentPosition);


    }


}

