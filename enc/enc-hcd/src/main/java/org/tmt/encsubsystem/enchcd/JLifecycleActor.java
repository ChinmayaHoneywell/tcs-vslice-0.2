package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import csw.framework.exceptions.FailureStop;
import csw.messages.commands.ControlCommand;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.client.internal.ActorRuntime;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
//import akka.actor.typed.javadsl.MutableBehavior;

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
    }

    public static final class SubmitCommandMessage implements LifecycleMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }


    private ActorContext<LifecycleMessage> actorContext;
    private JLoggerFactory loggerFactory;
    //private Config assemblyConfig;
    private ILogger log;
    private IConfigClientService configClientApi;
    private CommandResponseManager commandResponseManager;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JLifecycleActor(ActorContext<LifecycleMessage> actorContext, CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, IConfigClientService configClientApi, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.configClientApi = configClientApi;
        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;


    }

    public static <LifecycleMessage> Behavior<LifecycleMessage> behavior(CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, IConfigClientService configClientApi, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<LifecycleMessage>) new JLifecycleActor((ActorContext<JLifecycleActor.LifecycleMessage>) ctx, commandResponseManager, statePublisherActor, configClientApi, loggerFactory);
        });
    }


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
                        command -> {
                            log.debug(() -> "ShutdownMessage Received");
                            onShutdown(command);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("startup"),
                        command -> {
                            log.debug(() -> "StartUp Received");
                            handleStartupCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("shutdown"),
                        command -> {
                            log.debug(() -> "Shutdown Received");
                            handleShutdownCommand(command.controlCommand);
                            return Behaviors.same();
                        });

        return builder.build();
    }

    private void onInitialize(InitializeMessage message) {

        log.debug(() -> "Initialize Message Received ");
        Config assemblyConfig = getHCDConfig();

        // example of working with Config
        String ethernetaddress = assemblyConfig.getString("ethernetaddress");

        log.debug(() -> "ethernetaddress config element value is: " + ethernetaddress);
        statePublisherActor.tell(new JStatePublisherActor.StartMessage());
        message.cf.complete(null);

    }

    private void onShutdown(ShutdownMessage message) {
        log.debug(() -> "Shutdown Message Received ");
        JStatePublisherActor.StopMessage stopMessage = new JStatePublisherActor.StopMessage();
        statePublisherActor.tell(stopMessage);
    }

    private void handleStartupCommand(ControlCommand controlCommand) {
        log.debug(() -> "handle Startup Command = " + controlCommand);
        ActorRef<ControlCommand> startupCmdActor =
                actorContext.spawnAnonymous(JStartUpCmdActor.behavior(commandResponseManager, statePublisherActor, loggerFactory));
        startupCmdActor.tell(controlCommand);
    }

    private void handleShutdownCommand(ControlCommand controlCommand) {

        log.debug(() -> "handle shutdown Command = " + controlCommand);
        ActorRef<ControlCommand> shutdownCmdActor =
                actorContext.spawnAnonymous(JShutdownCmdActor.behavior(commandResponseManager, statePublisherActor, loggerFactory));

        shutdownCmdActor.tell(controlCommand);
    }


    /**
     * This method load assembly configuration.
     *
     * @return
     */
    private Config getHCDConfig() {

        try {
            //ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getHCDConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new JLifecycleActor.ConfigNotAvailableException();
        }

    }

    private ConfigData getHCDConfigData() throws ExecutionException, InterruptedException {

        log.debug(() -> "loading hcd configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/enc/enc_hcd.conf");

        ConfigData activeFile = configClientApi.getActive(filePath).get().get();

        return activeFile;
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }


}
