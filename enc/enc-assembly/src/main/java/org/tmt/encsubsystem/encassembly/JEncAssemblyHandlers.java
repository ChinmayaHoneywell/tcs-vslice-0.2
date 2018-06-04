package org.tmt.encsubsystem.encassembly;

import akka.actor.ActorRefFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.Materializer;
import com.typesafe.config.Config;
import csw.framework.exceptions.FailureStop;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandIssue;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.AkkaLocation;
import csw.messages.location.LocationRemoved;
import csw.messages.location.LocationUpdated;
import csw.messages.location.TrackingEvent;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.command.scaladsl.CurrentStateSubscription;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.client.internal.ActorRuntime;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to EncHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
public class JEncAssemblyHandlers extends JComponentHandlers {

    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    private ComponentInfo componentInfo;

    private IConfigClientService clientApi;

    private ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    private ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;
    //private ActorRef<JLifecycleActor.LifecycleMessage> lifecycleActor;
    private ActorRef<JMonitorActor.MonitorMessage> monitorActor;


    private Optional<JCommandService> hcdCommandService = Optional.empty();
    private Optional<CurrentStateSubscription> subscription = Optional.empty();

    JEncAssemblyHandlers(
            ActorContext<TopLevelActorMessage> ctx,
            ComponentInfo componentInfo,
            CommandResponseManager commandResponseManager,
            CurrentStatePublisher currentStatePublisher,
            ILocationService locationService,
            JLoggerFactory loggerFactory
    ) {
        super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory);
        this.currentStatePublisher = currentStatePublisher;
        this.log = loggerFactory.getLogger(getClass());
        this.commandResponseManager = commandResponseManager;
        this.actorContext = ctx;
        this.locationService = locationService;
        this.componentInfo = componentInfo;

        clientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);


        // Load the configuration from the configuration service
       // Config assemblyConfig = getAssemblyConfig();
        log.debug("Spawning Handler Actors in assembly");
        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(commandResponseManager, hcdCommandService, Boolean.TRUE, loggerFactory));

        eventHandlerActor = ctx.spawnAnonymous(JEventHandlerActor.behavior(loggerFactory));

       // lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(assemblyConfig, loggerFactory));

        monitorActor = ctx.spawnAnonymous(JMonitorActor.behavior(JMonitorActor.AssemblyState.Ready, JMonitorActor.AssemblyMotionState.Idle, loggerFactory));

    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        return CompletableFuture.runAsync(() -> {
            log.info("initializing enc assembly");
           // lifecycleActor.tell(new JLifecycleActor.InitializeMessage());
        });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.info("shutdown enc assembly");
           // lifecycleActor.tell(new JLifecycleActor.ShutdownMessage());
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.info("assembly getting notified - location changed ");
        if(trackingEvent instanceof LocationUpdated){
            AkkaLocation hcdAkkaLocation= (AkkaLocation)((LocationUpdated)trackingEvent).location();
            hcdCommandService = Optional.of(new JCommandService(hcdAkkaLocation, actorContext.getSystem()));
            // set up Hcd CurrentState subscription to be handled by the monitor actor
            subscription = Optional.of(hcdCommandService.get().subscribeCurrentState(currentState ->{
                        monitorActor.tell(new JMonitorActor.CurrentStateEventMessage(currentState));
                    }
                    ));

            log.info("connection to hcd from assembly received");

        }else if (trackingEvent instanceof LocationRemoved) {
            // do something for the tracked location when it is no longer available
            hcdCommandService = Optional.empty();
            // FIXME: not sure if this is necessary
            subscription.get().unsubscribe();
        }

        // send messages to command handler and monitor actors
        commandHandlerActor.tell(new JCommandHandlerActor.UpdateTemplateHcdMessage(hcdCommandService));

        monitorActor.tell(new JMonitorActor.LocationEventMessage(hcdCommandService));
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.info("validating command enc assembly "+ controlCommand.commandName().name());
        CommandResponse.Accepted accepted = new CommandResponse.Accepted(controlCommand.runId());
        if (controlCommand instanceof Setup) {
            if(controlCommand.commandName().name().equals("move")){
                log.info("Move command received by assembly");
             if(!((Setup) controlCommand).get("mode", JKeyTypes.StringKey()).isEmpty()) {
                 log.debug("move command has valid parameters");
                 return accepted;
             }
             else{
                 log.info("invalid move command parameters");
                 CommandResponse.Invalid invalid = new CommandResponse.Invalid(controlCommand.runId(),new CommandIssue.MissingKeyIssue("move command is missing mode parameter"));
                 return invalid;
             }
            }else if(controlCommand.commandName().name().equals("follow")){
                //TODO: Put validations
                return accepted;
            } else if(controlCommand.commandName().name().equals("immediate")){
                CommandResponse.Completed completed = new CommandResponse.Completed(controlCommand.runId());
                return completed;
            }
            log.info("invalid command");
            CommandResponse.Invalid invalid = new CommandResponse.Invalid(controlCommand.runId(),new CommandIssue.UnsupportedCommandIssue("this setup command is not supported"));
            return invalid;
        }
        return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("Only setup command supported"));
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.debug("in onSubmit()");
        commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.info("processing oneway command to enc assembly");
    }

    @Override
    public void onGoOffline() {
        log.debug("in onGoOffline()");
        commandHandlerActor.tell(new JCommandHandlerActor.GoOfflineMessage());
    }

    @Override
    public void onGoOnline() {
        log.debug("in onGoOnline()");

        commandHandlerActor.tell(new JCommandHandlerActor.GoOnlineMessage());
    }

    public class ConfigNotAvailableException extends FailureStop {

        public ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }

    /**
     * This method load assembly configuration. currently not used.
     * It may be moved to lifecycle actor and configuration will load during startup coomand execution.
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
            throw new ConfigNotAvailableException();
        }

    }

    private ConfigData getAssemblyConfigData() throws ExecutionException, InterruptedException {

        log.info("loading assembly configuration");

        // construct the path
        Path filePath = Paths.get("/org/tmt/tcs/tcs_test.conf");

        ConfigData activeFile = clientApi.getActive(filePath).get().get();

        return activeFile;
    }
}
