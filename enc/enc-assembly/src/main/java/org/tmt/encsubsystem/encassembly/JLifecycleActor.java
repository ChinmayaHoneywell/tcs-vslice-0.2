package org.tmt.encsubsystem.encassembly;

import akka.actor.ActorRefFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import akka.util.Timeout;
import com.typesafe.config.Config;
import csw.framework.exceptions.FailureStop;
import csw.messages.commands.ControlCommand;
import csw.services.command.CommandResponseManager;
import csw.services.command.javadsl.JCommandService;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.client.internal.ActorRuntime;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
//import akka.actor.typed.javadsl.MutableBehavior;

/**
 * Lifecycle Actor receive lifecycle messages and perform initialization, config loading, shutdown operations.
 */
public class JLifecycleActor extends MutableBehavior<JLifecycleActor.LifecycleMessage> {


    // add messages here
    interface LifecycleMessage {
    }

    public static final class InitializeMessage implements LifecycleMessage {
        public final CompletableFuture<Void> cf;

        public InitializeMessage(CompletableFuture<Void> cf) {
            this.cf = cf;
        }
    }

    public static final class ShutdownMessage implements LifecycleMessage {
        public final CompletableFuture<Void> cf;
        public ShutdownMessage(CompletableFuture<Void> cf) {
            this.cf =cf;
        }
    }

    public static final class SubmitCommandMessage implements LifecycleMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class UpdateHcdCommandServiceMessage implements LifecycleMessage {

        public final Optional<JCommandService> commandServiceOptional;

        public UpdateHcdCommandServiceMessage(Optional<JCommandService> commandServiceOptional) {
            this.commandServiceOptional = commandServiceOptional;
        }
    }


    private ActorContext<LifecycleMessage> actorContext;
    private JLoggerFactory loggerFactory;
    //private Config assemblyConfig;
    private ILogger log;
    private IConfigClientService configClientApi;
    private CommandResponseManager commandResponseManager;
    private Optional<JCommandService> hcdCommandService;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;


    private JLifecycleActor(ActorContext<LifecycleMessage> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, IConfigClientService configClientApi, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.configClientApi = configClientApi;
        this.commandResponseManager = commandResponseManager;
        this.hcdCommandService = hcdCommandService;
        this.commandHandlerActor = commandHandlerActor;
        this.eventHandlerActor = eventHandlerActor;

    }

    public static <LifecycleMessage> Behavior<LifecycleMessage> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, IConfigClientService configClientApi, ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor, ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<LifecycleMessage>) new JLifecycleActor((ActorContext<JLifecycleActor.LifecycleMessage>) ctx, commandResponseManager, hcdCommandService, configClientApi, commandHandlerActor, eventHandlerActor, loggerFactory);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Behaviors.Receive<LifecycleMessage> createReceive() {

        ReceiveBuilder<LifecycleMessage> builder = receiveBuilder()
                .onMessage(InitializeMessage.class,
                        command -> {
                            log.debug(() -> "InitializeMessage Received");
                            onInitialize(command);
                            return Behaviors.same();
                        })
                .onMessage(ShutdownMessage.class,
                        shutdownMessage -> {
                            log.debug(() -> "ShutdownMessage Received");
                            onShutdown(shutdownMessage);
                            return Behaviors.same();
                        })
                .onMessage(UpdateHcdCommandServiceMessage.class,
                        command -> {
                            log.debug(() -> "UpdateTemplateHcdMessage Received");
                            // update the template hcd
                            return behavior(commandResponseManager, command.commandServiceOptional, configClientApi, commandHandlerActor, eventHandlerActor, loggerFactory);
                        });
        return builder.build();
    }

    /**
     * This is called as part of csw component initialization
     * lifecycle actor will perform initialization activities like config loading inside it.
     * @param message
     */
    private void onInitialize(InitializeMessage message) {
        log.debug(() -> "Initialize Message Received ");
        eventHandlerActor.tell(new JEventHandlerActor.PublishAssemblyStateMessage());//change to ask pattern?
        eventHandlerActor.tell(new JEventHandlerActor.SubscribeEventMessage());
        Config assemblyConfig = getAssemblyConfig();
        // example of working with Config
        Double ventopenpercentage = assemblyConfig.getDouble("ventopenpercentage");
        log.debug(() -> "ventopenpercentage element value is: " + ventopenpercentage);
        //providing configuration to command actor for use in command.
        commandHandlerActor.tell(new JCommandHandlerActor.UpdateConfigMessage(Optional.of(assemblyConfig)));
        message.cf.complete(null);

    }

    /**
     * This is called as part of csw component shutdown.
     * Lifecycle actor will perform shutdown activities like releasing occupied resources if any, stop publishing events
     * @param message
     */
    private void onShutdown(ShutdownMessage message) {
        log.debug(() -> "Shutdown Message Received ");
        try {
            String cfString= AskPattern.ask(eventHandlerActor, (ActorRef<String> replyTo)->
                    new JEventHandlerActor.StopEventsMessage(replyTo), new Timeout(10, TimeUnit.SECONDS) , actorContext.getSystem().scheduler()
            ).toCompletableFuture().get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        message.cf.complete(null);

    }

    /**
     * This method load assembly configuration.
     *
     * @return
     */
    private Config getAssemblyConfig() {

        try {
            ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getAssemblyConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new JLifecycleActor.ConfigNotAvailableException();
        }

    }

    private ConfigData getAssemblyConfigData() throws ExecutionException, InterruptedException {

        log.debug(() -> "loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/enc/enc_assembly.conf");

        ConfigData activeFile = configClientApi.getActive(filePath).get().get();

        return activeFile;
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }


}
