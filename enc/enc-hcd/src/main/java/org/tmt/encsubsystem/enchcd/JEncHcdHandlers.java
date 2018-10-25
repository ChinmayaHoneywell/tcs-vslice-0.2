package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;
import csw.framework.CurrentStatePublisher;
import csw.framework.javadsl.JComponentHandlers;
import csw.messages.TopLevelActorMessage;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.AkkaLocation;
import csw.messages.location.LocationRemoved;
import csw.messages.location.LocationUpdated;
import csw.messages.location.TrackingEvent;
import csw.services.alarm.api.javadsl.IAlarmService;
import csw.services.command.CommandResponseManager;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CurrentStateSubscription;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.event.api.javadsl.IEventService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.encsubsystem.enchcd.models.HCDState;

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
public class JEncHcdHandlers extends JComponentHandlers {



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
    private Optional<CurrentStateSubscription> subscription = Optional.empty();

    JEncHcdHandlers(
            ActorContext<TopLevelActorMessage> ctx,
            ComponentInfo componentInfo,
            CommandResponseManager commandResponseManager,
            CurrentStatePublisher currentStatePublisher,
            ILocationService locationService,
            IEventService eventService,
            IAlarmService alarmService,
            JLoggerFactory loggerFactory
    ) {
        super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, alarmService,loggerFactory);
        this.currentStatePublisher = currentStatePublisher;
        this.log = loggerFactory.getLogger(getClass());
        this.commandResponseManager = commandResponseManager;
        this.actorContext = ctx;
        this.locationService = locationService;
        this.eventService = eventService;
        this.componentInfo = componentInfo;
        HCDState initialHcdState = new HCDState(HCDState.LifecycleState.Initialized, HCDState.OperationalState.Idle);

        configClientApi = JConfigClientFactory.clientApi(Adapter.toUntyped(actorContext.getSystem()), locationService);
        statePublisherActor = ctx.spawnAnonymous(JStatePublisherActor.behavior(componentInfo,currentStatePublisher, loggerFactory, initialHcdState));

        commandHandlerActor = ctx.spawnAnonymous(JCommandHandlerActor.behavior(commandResponseManager, loggerFactory, statePublisherActor));
        lifecycleActor = ctx.spawnAnonymous(JLifecycleActor.behavior(commandResponseManager, statePublisherActor, configClientApi, loggerFactory));


    }
    /**
     * This is a CSW Hook to initialize assembly.
     * This will get executed as part of hcd initialization after deployment.
     * @return
     */
    @Override
    public CompletableFuture<Void> jInitialize() {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        log.debug(() -> "initializing enc assembly");
        lifecycleActor.tell(new JLifecycleActor.InitializeMessage(cf));
        return cf;
    }
    /**
     * This is a CSW Hook to shutdown assembly.
     * This will get executed as part of hcd shutdown.
     * @return
     */
    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.debug(() -> "shutdown enc hcd");
            lifecycleActor.tell(new JLifecycleActor.ShutdownMessage());
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.debug(() -> "LocationEvent - " + trackingEvent);
        if (trackingEvent instanceof LocationUpdated) {
            AkkaLocation assemblyAkkaLocation = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
            JCommandService jCommandService= new JCommandService(assemblyAkkaLocation, actorContext.getSystem());
            // set up Hcd CurrentState subscription to be handled by the monitor actor
            subscription = Optional.of(jCommandService.subscribeCurrentState(reverseCurrentState -> {
                        statePublisherActor.tell(new JStatePublisherActor.ReverseCurrentStateMessage(reverseCurrentState));
                    }
            ));

            log.debug(() -> "connection to assembly received");

        } else if (trackingEvent instanceof LocationRemoved) {
            // FIXME: not sure if this is necessary
            subscription.ifPresent(subscription -> subscription.unsubscribe());
        }

    }
    /**
     * This is a CSW Validation hook. When command is submitted to this component
     * then first validation hook is called to validate command like parameter, value range.
     * @param controlCommand
     * @return
     */
    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.info(() -> "validating command in enc hcd");
        switch (controlCommand.commandName().name()) {
            case "follow":
                //Immediate command implementation, on submit hook will not be called.
                return executeFollowCommandAndReturnResponse(controlCommand);
            default:
                //accepting all commands
                return new CommandResponse.Accepted(controlCommand.runId());
        }
    }
    /**
     * This CSW hook is called after command is validated in validate hook.
     * Command is forwarded to Command Handler Actor or Lifecycle Actor for processing.
     * @param controlCommand
     */
    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.info(() -> "HCD , Command received - " + controlCommand);
        commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
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
