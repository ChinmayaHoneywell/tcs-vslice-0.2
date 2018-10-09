package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.util.Timeout;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.params.models.Prefix;
import csw.services.command.CommandResponseManager;
import csw.services.command.javadsl.JCommandService;

import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Assembly's Follow Command Actor.
 * This Actor submit follow command from assembly to hcd.
 */
public class JFollowCmdActor extends MutableBehavior<JFollowCmdActor.FollowMessage> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.
    interface FollowMessage {
    }

    public static final class FollowCommandMessage implements FollowMessage {

        public final ControlCommand controlCommand;
        public final ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo;


        public FollowCommandMessage(ControlCommand controlCommand, ActorRef<JCommandHandlerActor.ImmediateResponseMessage> replyTo) {
            this.controlCommand = controlCommand;
            this.replyTo = replyTo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FollowCommandMessage that = (FollowCommandMessage) o;
            return Objects.equals(controlCommand, that.controlCommand) &&
                    Objects.equals(replyTo, that.replyTo);
        }
    }

    private ActorContext<FollowMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private Optional<JCommandService> hcdCommandService;

    private Prefix encAssemblyPrefix = new Prefix("tcs.encA");


    private JFollowCmdActor(ActorContext<FollowMessage> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.hcdCommandService = hcdCommandService;


    }

    public static <FollowMessage> Behavior<FollowMessage> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<FollowMessage>) new JFollowCmdActor((ActorContext<JFollowCmdActor.FollowMessage>) ctx, commandResponseManager, hcdCommandService, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<FollowMessage> createReceive() {

        ReceiveBuilder<FollowMessage> builder = receiveBuilder()
                .onMessage(FollowCommandMessage.class,
                        followCommandMessage -> {
                            log.debug(() -> "Follow Command Message Received by FollowCmdActor in Assembly");
                            handleSubmitCommand(followCommandMessage);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    /**
     * This method handle follow command.
     * It forwards the command to hcd using hcd command service, receive response and
     * update the response back to 'replyTo' actor
     *
     * @param followCommandMessage
     */
    private void handleSubmitCommand(FollowCommandMessage followCommandMessage) {
        // NOTE: we use get instead of getOrElse because we assume the command has been validated
        ControlCommand command = followCommandMessage.controlCommand;

        if (hcdCommandService.isPresent()) {
            hcdCommandService.get()
                    .submitAndSubscribe(command, Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS))).thenAccept(response -> {
                followCommandMessage.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(response));
            });
        } else {
            followCommandMessage.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Error(command.runId(), "Can't locate TcsEncHcd")));
        }
    }


}
