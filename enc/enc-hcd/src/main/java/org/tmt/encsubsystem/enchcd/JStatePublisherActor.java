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
    interface StatePublisherMessage {}

    public static final class StartMessage implements StatePublisherMessage { }
    public static final class StopMessage implements StatePublisherMessage { }
    public static final class PublishMessage implements StatePublisherMessage { }


    private JLoggerFactory loggerFactory;
    private CurrentStatePublisher currentStatePublisher;
    private ILogger log;
    private TimerScheduler<StatePublisherMessage> timer;


    //prefix
    String prefix = "tcs.test";

    //keys
    Key timestampKey    = JKeyTypes.TimestampKey().make("timestampKey");

    Key azPosKey        = JKeyTypes.DoubleKey().make("azPosKey");
    Key azPosErrorKey   = JKeyTypes.DoubleKey().make("azPosErrorKey");
    Key elPosKey        = JKeyTypes.DoubleKey().make("elPosKey");
    Key elPosErrorKey   = JKeyTypes.DoubleKey().make("elPosErrorKey");
    Key azInPositionKey = JKeyTypes.BooleanKey().make("azInPositionKey");
    Key elInPositionKey = JKeyTypes.BooleanKey().make("elInPositionKey");

    private static final Object TIMER_KEY = new Object();

    private JStatePublisherActor(TimerScheduler<StatePublisherMessage> timer, CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory) {
        this.timer = timer;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(this.getClass());
        this.currentStatePublisher = currentStatePublisher;
    }

    public static <StatePublisherMessage> Behavior<StatePublisherMessage> behavior(CurrentStatePublisher currentStatePublisher, JLoggerFactory loggerFactory) {
        return Behaviors.withTimers(timers -> {
            return (Behaviors.MutableBehavior<StatePublisherMessage>) new JStatePublisherActor((TimerScheduler<JStatePublisherActor.StatePublisherMessage>)timers, currentStatePublisher, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<StatePublisherMessage> createReceive() {

        ReceiveBuilder<StatePublisherMessage> builder = receiveBuilder()
                .onMessage(StartMessage.class,
                        command -> {
                            log.info("StartMessage Received");
                            onStart(command);
                            return Behaviors.same();
                        })
                .onMessage(StopMessage.class,
                        command -> {
                            log.info("StopMessage Received");
                            onStop(command);
                            return Behaviors.same();
                        })
                .onMessage(PublishMessage.class,
                        command -> {
                            log.info("PublishMessage Received");
                            onPublishMessage(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void onStart(StartMessage message) {

        log.info("Start Message Received ");

        timer.startPeriodicTimer(TIMER_KEY, new PublishMessage(), Duration.create(600, TimeUnit.SECONDS));

        log.info("start message completed");


    }

    private void onStop(StopMessage message) {

        log.info("Stop Message Received ");
    }

    private void onPublishMessage(PublishMessage message) {

        log.info("Publish Message Received ");

        // example parameters for a current state

        Parameter azPosParam        = azPosKey.set(35.34).withUnits(degree);
        Parameter azPosErrorParam   = azPosErrorKey.set(0.34).withUnits(degree);
        Parameter elPosParam        = elPosKey.set(46.7).withUnits(degree);
        Parameter elPosErrorParam   = elPosErrorKey.set(0.03).withUnits(degree);
        Parameter azInPositionParam = azInPositionKey.set(false);
        Parameter elInPositionParam = elInPositionKey.set(true);

        Parameter timestamp = timestampKey.set(Instant.now());

        //create CurrentState and use sequential add
        CurrentState currentState = new CurrentState(prefix)
                .add(azPosParam)
                .add(elPosParam)
                .add(azPosErrorParam)
                .add(elPosErrorParam)
                .add(azInPositionParam)
                .add(elInPositionParam)
                .add(timestamp);

        currentStatePublisher.publish(currentState);


    }


}

