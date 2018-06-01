package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import com.typesafe.config.Config;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JPkLifecycleActor extends Behaviors.MutableBehavior<org.tmt.tcs.pk.pkassembly.JPkLifecycleActor.LifecycleMessage> {


    // add messages here
    interface LifecycleMessage {}

    public static final class InitializeMessage implements LifecycleMessage { }
    public static final class ShutdownMessage implements LifecycleMessage { }


    private ActorContext<LifecycleMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;


    private JPkLifecycleActor(ActorContext<LifecycleMessage> actorContext, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

    }

    public static <LifecycleMessage> Behavior<LifecycleMessage> behavior(JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<LifecycleMessage>) new org.tmt.tcs.pk.pkassembly.JPkLifecycleActor((ActorContext<org.tmt.tcs.pk.pkassembly.JPkLifecycleActor.LifecycleMessage>) ctx, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<LifecycleMessage> createReceive() {

        ReceiveBuilder<LifecycleMessage> builder = receiveBuilder()
                .onMessage(InitializeMessage.class,
                        command -> {
                            log.info("Inside JPkLifecycleActor: InitializeMessage Received");
                            onInitialize(command);
                            return Behaviors.same();
                        })
                .onMessage(ShutdownMessage.class,
                        command -> {
                        log.info("Inside JPkLifecycleActor: ShutdownMessage Received");
                        onShutdown(command);
                        return Behaviors.same();
                });
        return builder.build();
    }

    private void onInitialize(InitializeMessage message) {

        log.info("Inside JPkLifecycleActor: Initialize Message Received ");
    }

    private void onShutdown(ShutdownMessage message) {

        log.info("Inside JPkLifecycleActor: Shutdown Message Received ");
    }


}
