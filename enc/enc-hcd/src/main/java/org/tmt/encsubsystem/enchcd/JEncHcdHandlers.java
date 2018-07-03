package org.tmt.encsubsystem.enchcd;

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
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.event.javadsl.IEventService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

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
public class JEncHcdHandlers extends JComponentHandlers {
    // what shouldb be the initial state when hcd is just deployed, even before the onInitialize hook get called.
    public enum LifecycleState {
        Initialized, Running
    }

    public enum OperationalState {
        Idle, Ready, Following, InPosition
    }


    private ILogger log;
    private IConfigClientService configClientApi;
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    IEventService eventService;
    private ComponentInfo componentInfo;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;
    ActorRef<JLifecycleActor.LifecycleMessage> lifecycleActor;

    JEncHcdHandlers(
            ActorContext<TopLevelActorMessage> ctx,
            ComponentInfo componentInfo,
            CommandResponseManager commandResponseManager,
            CurrentStatePublisher currentStatePublisher,
            ILocationService locationService,
            IEventService eventService,
            JLoggerFactory loggerFactory
    ) {
        super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, loggerFactory);
        this.currentStatePublisher = currentStatePublisher;
        this.log = loggerFactory.getLogger(getClass());
        this.commandResponseManager = commandResponseManager;
        this.actorContext = ctx;
        this.locationService = locationService;
        this.eventService = eventService;
        this.componentInfo = componentInfo;
        configClientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);
        statePublisherActor = ctx.spawnAnonymous(JStatePublisherActor.behavior(currentStatePublisher, loggerFactory, LifecycleState.Initialized, OperationalState.Idle));

        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(commandResponseManager, loggerFactory, statePublisherActor));
        lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(commandResponseManager, statePublisherActor, configClientApi, loggerFactory));
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        log.debug(() -> "initializing enc assembly");
        lifecycleActor.tell(new JLifecycleActor.InitializeMessage(cf));
        return cf;
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.debug(() -> "shutdown enc hcd");
            lifecycleActor.tell(new JLifecycleActor.ShutdownMessage());
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(() -> "location changed " + trackingEvent);
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.debug(() -> "validating command in enc hcd");
        switch (controlCommand.commandName().name()) {
            case "follow":
                //Immediate command implementation, on submit hook will not be called.
                return executeFollowCommandAndReturnResponse(controlCommand);
            default:
                //accepting all commands
                return new CommandResponse.Accepted(controlCommand.runId());
        }
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.info(() -> "HCD , Command received - " + controlCommand);
        switch (controlCommand.commandName().name()) {
            case "startup":
                log.debug(() -> "handling startup command: " + controlCommand);
                lifecycleActor.tell(new JLifecycleActor.SubmitCommandMessage(controlCommand));
                break;
            case "shutdown":
                log.debug(() -> "handling shutdown command: " + controlCommand);
                lifecycleActor.tell(new JLifecycleActor.SubmitCommandMessage(controlCommand));
                break;
            case "fastMove":
                log.debug(() -> "handling fastMove command: " + controlCommand);
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;

            case "trackOff":
                log.debug(() -> "handling trackOff command: " + controlCommand);
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;

            default:
                log.error("unhandled command in HCD: " + controlCommand);
                commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("Please check command name, it is not supported by ENC HCD")));

        }
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.debug(() -> "processing one way command to enc hcd");
    }

    @Override
    public void onGoOffline() {
        log.info(() -> "HCD Go Offline hook");
    }

    @Override
    public void onGoOnline() {
        log.info(() -> "HCD Go Online hook");
    }

    /**
     * This method send command to command handler and return response of execution.
     * The command execution is blocking, response is not return until command processing is completed
     *
     * @param controlCommand
     * @return
     */
    private CommandResponse executeFollowCommandAndReturnResponse(ControlCommand controlCommand) {
        //submitting command to commandHandler actor and waiting for completion.
        try {
            CompletionStage<JCommandHandlerActor.ImmediateResponseMessage> reply = AskPattern.ask(commandHandlerActor, (ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo) ->
                    new JCommandHandlerActor.ImmediateCommandMessage(controlCommand, replyTo), new Timeout(10, TimeUnit.SECONDS), actorContext.getSystem().scheduler());

            return reply.toCompletableFuture().get().commandResponse;
        } catch (Exception e) {
            return new CommandResponse.Error(controlCommand.runId(), "Error occurred while executing follow command");
        }
    }

}
