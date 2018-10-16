package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.javadsl.TimerScheduler;
import csw.framework.CurrentStatePublisher;
import csw.messages.framework.ComponentInfo;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.messages.params.states.StateName;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.encsubsystem.enchcd.simplesimulator.CurrentPosition;
import org.tmt.encsubsystem.enchcd.simplesimulator.Health;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static csw.messages.javadsl.JUnits.degree;
import static org.tmt.encsubsystem.enchcd.Constants.CURRENT_POSITION_PUBLISH_FREQUENCY;
import static org.tmt.encsubsystem.enchcd.Constants.HEALTH_PUBLISH_FREQUENCY;


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

    public static final class PublishCurrentPositionMessage implements StatePublisherMessage {
    }

    public static final class PublishHealthMessage implements StatePublisherMessage {
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
    private ComponentInfo componentInfo;

    JEncHcdHandlers.LifecycleState lifecycleState;
    JEncHcdHandlers.OperationalState operationalState;

    //Keys to represent lifecycle state and operational state, parameters will be created from these keys
    private Key<String> lifecycleKey = JKeyTypes.StringKey().make("LifecycleState");
    private Key<String> operationalKey = JKeyTypes.StringKey().make("OperationalState");

    //keys for current position
    private Key<Double> basePosKey = JKeyTypes.DoubleKey().make("basePosKey");
    private Key<Double> capPosKey = JKeyTypes.DoubleKey().make("capPosKey");

    //keys for health
    private Key<String> healthKey = JKeyTypes.StringKey().make("healthKey");
    private Key<String> healthReasonKey = JKeyTypes.StringKey().make("healthReasonKey");
    private Key<Instant> healthTimeKey = JKeyTypes.TimestampKey().make("healthTimeKey");


    //this is the time when subsystem sampled the data.
    private Key<Instant> ecsSubsystemTimestampKey = JKeyTypes.TimestampKey().make("subsystemTimestampKey");
    //this is the time when ENC HCD processed the data
    private Key<Instant> encHcdTimestampKey = JKeyTypes.TimestampKey().make("hcdTimestampKey");


    private static final Object TIMER_KEY_CURRENT_POSITION = new Object();
    private static final Object TIMER_KEY_HEALTH = new Object();

    private JStatePublisherActor(TimerScheduler<StatePublisherMessage> timer, ComponentInfo componentInfo, CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory, JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
        this.timer = timer;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(this.getClass());
        this.currentStatePublisher = currentStatePublisher;
        this.lifecycleState = lifecycleState;
        this.operationalState = operationalState;
        this.componentInfo = componentInfo;
    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(ComponentInfo componentInfo, CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory, JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
        return Behaviors.withTimers(timers -> {
            return (MutableBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, componentInfo, currentStatePublisher, loggerFactory, lifecycleState, operationalState);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
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
                .onMessage(PublishCurrentPositionMessage.class,
                        publishCurrentPositionMessage -> {
                            log.debug(() -> "PublishCurrentPositionMessage Received");
                            publishCurrentPosition();
                            return Behaviors.same();
                        })
                .onMessage(PublishHealthMessage.class,
                        publishHealthMessage -> {
                            log.debug(() -> "PublishCurrentPositionMessage Received");
                            publishHealth();
                            return Behaviors.same();
                        })
                .onMessage(StateChangeMessage.class,
                        command -> {
                            log.debug(() -> "StateChangeMessage Received");
                            handleStateChange(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void onStart(StartMessage message) {
        log.debug(() -> "Start Message Received ");
        timer.startPeriodicTimer(TIMER_KEY_CURRENT_POSITION, new PublishCurrentPositionMessage(), Duration.ofMillis(1000/CURRENT_POSITION_PUBLISH_FREQUENCY));
        timer.startPeriodicTimer(TIMER_KEY_HEALTH, new PublishHealthMessage(), Duration.ofMillis(1000/HEALTH_PUBLISH_FREQUENCY));
        log.debug(() -> "start message completed");
    }

    /**
     * This method will stop all timers i.e. it will stop publishing all current states from HCD.
     * @param message
     */
    private void onStop(StopMessage message) {

        log.debug(() -> "Stop Message Received ");
        timer.cancel(TIMER_KEY_CURRENT_POSITION);
        timer.cancel(TIMER_KEY_HEALTH);
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
        CurrentState currentState = new CurrentState(componentInfo.prefix().prefix(), new StateName(Constants.HCD_STATE))
                .madd(lifecycleKey.set(lifecycleState.name()),operationalKey.set(operationalState.name()));
        currentStatePublisher.publish(currentState);
    }

    /**
     * This method get current position from subsystem and
     * publish it using current state publisher as per timer frequency.
     */
    private void publishCurrentPosition() {
        // example parameters for a current state
        CurrentPosition currentPosition = SimpleSimulator.getInstance().getCurrentPosition();

        Parameter<Double> basePosParam = basePosKey.set(currentPosition.getBase()).withUnits(degree);
        Parameter<Double> capPosParam = capPosKey.set(currentPosition.getCap()).withUnits(degree);
        //this is the time when subsystem published current position.
        Parameter<Instant> ecsSubsystemTimestampParam = ecsSubsystemTimestampKey.set(Instant.ofEpochMilli(currentPosition.getTime()));
        //this is the time when ENC HCD processed current position
        Parameter<Instant> encHcdTimestampParam = encHcdTimestampKey.set(Instant.now());

        CurrentState currentStatePosition = new CurrentState(componentInfo.prefix().prefix(), new StateName(Constants.CURRENT_POSITION))
                .add(basePosParam)
                .add(capPosParam)
                .add(ecsSubsystemTimestampParam)
                .add(encHcdTimestampParam);

        currentStatePublisher.publish(currentStatePosition);
     }

    /**
     * This method get current health from subsystem and
     * publish it using current state publisher as per timer frequency.
     */
    private void publishHealth() {
        Health health = SimpleSimulator.getInstance().getHealth();
        Parameter<String> healthParam = healthKey.set(health.getHealth().name());
        Parameter<String> healthReasonParam = healthReasonKey.set(health.getReason());
        Parameter<Instant> healthTimeParam = healthTimeKey.set(Instant.ofEpochMilli(health.getTime()));

        CurrentState currentStateHealth = new CurrentState(componentInfo.prefix().prefix(), new StateName(Constants.HEALTH))
                .add(healthParam)
                .add(healthReasonParam)
                .add(healthTimeParam);
        currentStatePublisher.publish(currentStateHealth);
    }


}

