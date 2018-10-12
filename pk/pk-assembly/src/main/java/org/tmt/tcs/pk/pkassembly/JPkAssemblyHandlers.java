package org.tmt.tcs.pk.pkassembly;

import akka.actor.ActorRefFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import csw.framework.exceptions.FailureStop;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.CurrentStatePublisher;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.TopLevelActorMessage;
import csw.services.alarm.api.javadsl.IAlarmService;
import csw.services.command.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.client.internal.ActorRuntime;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.event.api.javadsl.IEventService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to PkHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
public class JPkAssemblyHandlers extends JComponentHandlers {

    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    private ComponentInfo componentInfo;
    private IConfigClientService clientApi;

    private ActorRef<JPkCommandHandlerActor.CommandMessage> commandHandlerActor;
    private ActorRef<JPkLifecycleActor.LifecycleMessage> lifecycleActor;
    private ActorRef<JPkEventHandlerActor.EventMessage> eventHandlerActor;

    JPkAssemblyHandlers(
            ActorContext<TopLevelActorMessage> ctx,
            ComponentInfo componentInfo,
            CommandResponseManager commandResponseManager,
            CurrentStatePublisher currentStatePublisher,
            ILocationService locationService,
            IEventService eventService,
            IAlarmService alarmService,
            JLoggerFactory loggerFactory
    ) {
        super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, alarmService, loggerFactory);
        this.currentStatePublisher = currentStatePublisher;
        this.log = loggerFactory.getLogger(getClass());
        this.commandResponseManager = commandResponseManager;
        this.actorContext = ctx;
        this.locationService = locationService;
        this.componentInfo = componentInfo;


        // Handle to the config client service
        clientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);


        // Load the configuration from the configuration service
        //Config assemblyConfig = getAssemblyConfig();

        lifecycleActor = ctx.spawnAnonymous(JPkLifecycleActor.behavior(loggerFactory));
        eventHandlerActor = ctx.spawnAnonymous(JPkEventHandlerActor.behavior(eventService, loggerFactory));
        commandHandlerActor = ctx.spawnAnonymous(JPkCommandHandlerActor.behavior(commandResponseManager, Boolean.TRUE, loggerFactory, eventHandlerActor));
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        return CompletableFuture.runAsync(() -> {
            log.debug("Inside JPkAssemblyHandlers: initialize()");
        });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.debug("Inside JPkAssemblyHandlers: onShutdown()");
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug("Inside JPkAssemblyHandlers: onLocationTrackingEvent()");
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: validateCommand()");

        return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: onSubmit()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.SubmitCommandMessage(controlCommand));
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug("Inside JPkAssemblyHandlers: onOneway()");
    }

    @Override
    public void onGoOffline() {
        log.debug("Inside JPkAssemblyHandlers: onGoOffline()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.GoOfflineMessage());
    }

    @Override
    public void onGoOnline() {
        log.debug("Inside JPkAssemblyHandlers: onGoOnline()");

        commandHandlerActor.tell(new JPkCommandHandlerActor.GoOnlineMessage());
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Inside JPkAssemblyHandlers: Configuration not available. Initialization failure.");
        }
    }

    private Config getAssemblyConfig() {

        try {
            ActorRefFactory actorRefFactory = Adapter.toUntyped(actorContext.getSystem());

            ActorRuntime actorRuntime = new ActorRuntime(Adapter.toUntyped(actorContext.getSystem()));

            Materializer mat = actorRuntime.mat();

            ConfigData configData = getAssemblyConfigData();

            return configData.toJConfigObject(mat).get();

        } catch (Exception e) {
            throw new ConfigNotAvailableException();
        }

    }

    private ConfigData getAssemblyConfigData() throws ExecutionException, InterruptedException {

        log.info("Inside JPkAssemblyHandlers: loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/tcs_test.conf");

        ConfigData activeFile = clientApi.getActive(filePath).get().get();

        return activeFile;
    }
}
