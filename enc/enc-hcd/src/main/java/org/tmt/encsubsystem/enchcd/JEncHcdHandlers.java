package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandIssue;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.concurrent.CompletableFuture;

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
    private CommandResponseManager commandResponseManager;
    private CurrentStatePublisher currentStatePublisher;
    private ActorContext<TopLevelActorMessage> actorContext;
    private ILocationService locationService;
    private ComponentInfo componentInfo;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;
    ActorRef<JCommandHandlerActor.CommandMessage> commandHandlerActor;

    JEncHcdHandlers(
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

        statePublisherActor = ctx.spawnAnonymous(JStatePublisherActor.behavior(currentStatePublisher,loggerFactory));

        commandHandlerActor =  ctx.spawnAnonymous(JCommandHandlerActor.behavior(commandResponseManager, loggerFactory));
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
        return CompletableFuture.runAsync(() -> {
            log.info("initializing enc hcd");

                JStatePublisherActor.StartMessage message = new JStatePublisherActor.StartMessage();

                statePublisherActor.tell(message);

            });

    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            log.info("shutdown enc hcd");
        });
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        log.info("location changed " + trackingEvent);
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        log.info("validating command in enc hcd");
        return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
        log.info("processing submitted command to enc hcd");
        switch (controlCommand.commandName().name()) {

            case "fastMove":
                log.debug("handling fastMove command: " + controlCommand);
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;

            case "trackOff":
                log.debug("handling trackOff command: " + controlCommand);
                commandHandlerActor.tell(new JCommandHandlerActor.SubmitCommandMessage(controlCommand));
                break;

            default:
                log.error("unhandled command in HCD: " + controlCommand);
                commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue("Please check command name, it is not supported by ENC HCD")));

        }
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
        log.info("processing oneway command to enc hcd");
    }

    @Override
    public void onGoOffline() {

    }

    @Override
    public void onGoOnline() {

    }
}
