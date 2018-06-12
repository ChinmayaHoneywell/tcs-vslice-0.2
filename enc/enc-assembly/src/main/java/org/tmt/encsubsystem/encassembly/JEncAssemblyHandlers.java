package org.tmt.encsubsystem.encassembly;


import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;
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
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to EncHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
public class JEncAssemblyHandlers extends JComponentHandlers {


    // what should be the initial state when assembly is just deployed, even before the onInitialize hook get called.
    public enum AssemblyLifecycleState {
        Initialized, Running, Offline, Online
    }

    public enum AssemblyOperationalState {
        Idle, Ready, Slewing, Tracking, InPosition, Halted, Faulted
    }

    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    private ComponentInfo componentInfo;

    private IConfigClientService configClientApi;

    private ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    private ActorRef<JEventHandlerActor.EventMessage> eventHandlerActor;
    private ActorRef<JLifecycleActor.LifecycleMessage> lifecycleActor;
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

        configClientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);


        // Load the configuration from the configuration service
        // Config assemblyConfig = getAssemblyConfig();
        log.debug(()->"Spawning Handler Actors in assembly");
        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(commandResponseManager, hcdCommandService, Boolean.TRUE, loggerFactory));

        eventHandlerActor = ctx.spawnAnonymous(JEventHandlerActor.behavior(loggerFactory));

        lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(commandResponseManager, hcdCommandService, configClientApi, loggerFactory));

        monitorActor = ctx.spawnAnonymous(JMonitorActor.behavior(JEncAssemblyHandlers.AssemblyLifecycleState.Initialized, JEncAssemblyHandlers.AssemblyOperationalState.Idle, loggerFactory, eventHandlerActor, commandHandlerActor));

    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        return CompletableFuture.runAsync(() -> {
            log.debug(()->"initializing enc assembly");
            lifecycleActor.tell(new JLifecycleActor.InitializeMessage());
        });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.debug(()->"shutdown enc assembly");
            lifecycleActor.tell(new JLifecycleActor.ShutdownMessage());
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(()->"assembly getting notified - location changed ");
        if (trackingEvent instanceof LocationUpdated) {
            AkkaLocation hcdAkkaLocation = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
            hcdCommandService = Optional.of(new JCommandService(hcdAkkaLocation, actorContext.getSystem()));
            // set up Hcd CurrentState subscription to be handled by the monitor actor
            subscription = Optional.of(hcdCommandService.get().subscribeCurrentState(currentState -> {
                        log.debug(()->"Assembly getting notified: " + currentState.prefix());
                        monitorActor.tell(new JMonitorActor.CurrentStateEventMessage(currentState));
                    }
            ));

            log.debug(()->"connection to hcd from assembly received");

        } else if (trackingEvent instanceof LocationRemoved) {
            // do something for the tracked location when it is no longer available
            hcdCommandService = Optional.empty();
            // FIXME: not sure if this is necessary
            subscription.get().unsubscribe();
        }

        // send messages to command handler and monitor actors
        commandHandlerActor.tell(new JCommandHandlerActor.UpdateTemplateHcdMessage(hcdCommandService));
        lifecycleActor.tell(new JLifecycleActor.UpdateHcdCommandServiceMessage(hcdCommandService));
        monitorActor.tell(new JMonitorActor.LocationEventMessage(hcdCommandService));
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.debug(()->"validating command enc assembly " + controlCommand.commandName().name());
        CommandResponse.Accepted accepted = new CommandResponse.Accepted(controlCommand.runId());

        if (controlCommand instanceof Setup) {
            if (controlCommand.commandName().name().equals("move")) {
                log.debug(()->"Move command received by assembly");
                if (!((Setup) controlCommand).get("mode", JKeyTypes.StringKey()).isEmpty()) {
                    log.debug(() -> "move command has valid parameters");
                    log.debug(() -> "asking assembly lifecycle state and operational state from monitor actor");
                    try {
                        JMonitorActor.AssemblyStatesResponseMessage assemblyStates = AskPattern.ask(monitorActor, (ActorRef<JMonitorActor.AssemblyStatesResponseMessage> replyTo) ->
                                        new JMonitorActor.AssemblyStatesAskMessage(replyTo)
                                , new Timeout(10, TimeUnit.SECONDS), actorContext.getSystem().scheduler()).toCompletableFuture().get();
                        log.debug(() -> "Got Assembly state from monitor actor - " + assemblyStates.assemblyOperationalState + " ,  " + assemblyStates.assemblyLifecycleState);
                        if (assemblyStates.assemblyOperationalState == AssemblyOperationalState.Ready) {
                            log.debug(()->"Assembly is ready to accept command");
                            return accepted;
                        } else {
                            CommandResponse invalid = new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not ready to accept operational command"));
                            return invalid;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        CommandResponse invalid = new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.OtherIssue("Exception while querying assembly state"));
                        return invalid;
                    }
                } else {
                    log.debug(()->"invalid move command parameters");
                    CommandResponse.Invalid invalid = new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.MissingKeyIssue("move command is missing mode parameter"));

                    return invalid;
                }
            } else if (controlCommand.commandName().name().equals("follow")) {
                //TODO: Put validations
                try {
                    log.debug(()->"Follow command submitting to command handler from assembly and waiting for response");
                    //submitting command to commandHandler actor and waiting for completion.
                    final CompletionStage<JCommandHandlerActor.ImmediateResponseMessage> reply = AskPattern.ask(commandHandlerActor, (ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo) ->
                            new JCommandHandlerActor.ImmediateCommandMessage(controlCommand, replyTo), new Timeout(10, TimeUnit.SECONDS), actorContext.getSystem().scheduler());
                    CommandResponse response = reply.toCompletableFuture().get().commandResponse;
                    log.debug(()->"follow command response received in validate hook of assembly");
                    return response;
                } catch (Exception e) {
                    e.printStackTrace();
                    return new CommandResponse.Error(controlCommand.runId(), "Error occurred while executing follow command");
                }


            } else if (controlCommand.commandName().name().equals("immediate")) {
                CommandResponse.Completed completed = new CommandResponse.Completed(controlCommand.runId());
                return completed;
            } else if (controlCommand.commandName().name().equals("startup")) {
                return accepted;
            } else if (controlCommand.commandName().name().equals("shutdown")) {
                return accepted;
            } else {
                log.debug(()->"invalid command");
                CommandResponse.Invalid invalid = new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("this setup command is not supported"));
                return invalid;
            }
        } else {
            return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("Only setup command supported"));
        }
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.info(()-> "Assembly received command - " + controlCommand);
        switch (controlCommand.commandName().name()) {
            case "move":
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;
            case "follow":
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;
            case "startup":
                lifecycleActor.tell(new JLifecycleActor.SubmitCommandMessage(controlCommand));
                break;
            case "shutdown":
                lifecycleActor.tell(new JLifecycleActor.SubmitCommandMessage(controlCommand));
                break;

        }


    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug(()->"processing oneway command to enc assembly");
    }

    @Override
    public void onGoOffline() {
        log.debug(()->"in onGoOffline()");
        commandHandlerActor.tell(new JCommandHandlerActor.GoOfflineMessage());
    }

    @Override
    public void onGoOnline() {
        log.debug(()->"in onGoOnline()");

        commandHandlerActor.tell(new JCommandHandlerActor.GoOnlineMessage());
    }


}
