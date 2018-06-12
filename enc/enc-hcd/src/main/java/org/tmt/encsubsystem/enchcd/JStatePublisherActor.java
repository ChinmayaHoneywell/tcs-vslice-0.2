package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.actor.typed.javadsl.TimerScheduler;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.generics.Parameter;
import csw.messages.params.states.CurrentState;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.Duration;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static csw.messages.javadsl.JUnits.degree;


public class JStatePublisherActor extends Behaviors.MutableBehavior<JStatePublisherActor.StatePublisherMessage> {


    // add messages here
    interface StatePublisherMessage {
    }

    public static final class StartMessage implements StatePublisherMessage {
    }

    public static final class StopMessage implements StatePublisherMessage {
    }

    public static final class PublishMessage implements StatePublisherMessage {
    }


    public static final class StateChangeMessage implements StatePublisherMessage {

        public final JEncHcdHandlers.LifecycleState lifecycleState;
        public final JEncHcdHandlers.OperationalState operationalState;

        public StateChangeMessage(JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
            this.lifecycleState = lifecycleState;
            this.operationalState = operationalState;
        }
    }

    private JLoggerFactory loggerFactory;
    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;

    JEncHcdHandlers.LifecycleState lifecycleState;
    JEncHcdHandlers.OperationalState operationalState;


    //prefix
    String prefixCurrentPosition = "tmt.tcs.ecs.currentPosition";
    String prefixCurrentLifecycleState = "tmt.tcs.ecs.currentLifecycleState";

    //keys
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
    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory, JEncHcdHandlers.LifecycleState lifecycleState, JEncHcdHandlers.OperationalState operationalState) {
        return Behaviors.withTimers(timers -> {
            return (Behaviors.MutableBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>) timers, currentStatePublisher, loggerFactory, lifecycleState, operationalState);
        });
    }


    @Override
    public Behaviors.Receive<StatePublisherMessage> createReceive() {

        ReceiveBuilder<StatePublisherMessage> builder = receiveBuilder()
                .onMessage(StartMessage.class,
                        command -> {
                            log.debug("StartMessage Received");
                            onStart(command);
                            return Behaviors.same();
                        })
                .onMessage(StopMessage.class,
                        command -> {
                            log.debug("StopMessage Received");
                            onStop(command);
                            return Behaviors.same();
                        })
                .onMessage(PublishMessage.class,
                        command -> {
                            log.debug("PublishMessage Received");
                            onPublishMessage(command);
                            return Behaviors.same();
                        })
                .onMessage(StateChangeMessage.class,
                        command -> {
                            log.debug("LifecycleStateChangeMessage Received");
                            handleStateChange(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void onStart(StartMessage message) {

        log.debug("Start Message Received ");

        timer.startPeriodicTimer(TIMER_KEY, new PublishMessage(), Duration.create(60, TimeUnit.SECONDS));

        log.debug("start message completed");


    }

    private void onStop(StopMessage message) {

        log.debug("Stop Message Received ");
    }

    /**
     * This method update state of hcd. this changed state will be published to assembly as per timer frequency.
     *
     * @param message
     */
    private void handleStateChange(StateChangeMessage message) {
        this.lifecycleState = message.lifecycleState;
        this.operationalState = message.operationalState;
        CurrentState currentLifecycleState = new CurrentState(prefixCurrentLifecycleState)
                .add(JKeyTypes.StringKey().make("LifecycleState").set(lifecycleState.name()))
                .add(JKeyTypes.StringKey().make("OperationalState").set(operationalState.name()));


        currentStatePublisher.publish(currentLifecycleState);
    }

    private void onPublishMessage(PublishMessage message) {

        log.debug("Publish Message Received ");

        // example parameters for a current state

        Parameter azPosParam = azPosKey.set(35.34).withUnits(degree);
        Parameter azPosErrorParam = azPosErrorKey.set(0.34).withUnits(degree);
        Parameter elPosParam = elPosKey.set(46.7).withUnits(degree);
        Parameter elPosErrorParam = elPosErrorKey.set(0.03).withUnits(degree);
        Parameter azInPositionParam = azInPositionKey.set(false);
        Parameter elInPositionParam = elInPositionKey.set(true);

        Parameter timestamp = timestampKey.set(Instant.now());

        //create CurrentState and use sequential add
        CurrentState currentPosition = new CurrentState(prefixCurrentPosition)
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

