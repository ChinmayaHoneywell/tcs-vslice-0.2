package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.services.command.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.encsubsystem.enchcd.models.FollowCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

import java.util.Objects;

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
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JFollowCmdActor(ActorContext<FollowMessage> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;


    }

    public static <FollowMessage> Behavior<FollowMessage> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<FollowMessage>) new JFollowCmdActor((ActorContext<JFollowCmdActor.FollowMessage>) ctx, commandResponseManager, loggerFactory, statePublisherActor);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Behaviors.Receive<FollowMessage> createReceive() {

        ReceiveBuilder<FollowMessage> builder = receiveBuilder()
                .onMessage(FollowCommandMessage.class,
                        message -> {
                            handleSubmitCommand(message);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    /**
     * This method process Follow command.
     * It is assumed that all the validation have been done at ComponentHandler and CommandHandler.
     *
     * @param message
     */
    private void handleSubmitCommand(FollowCommandMessage message) {
        log.debug(() -> "HCD handling follow command = " + message);
        FollowCommand.Response response = SimpleSimulator.getInstance().sendCommand(new FollowCommand());
        switch (response.getStatus()){
            case OK:
                message.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Completed(message.controlCommand.runId())));
                statePublisherActor.tell(new JStatePublisherActor.FollowCommandCompletedMessage());
                break;
            case ERROR:
                message.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Error(message.controlCommand.runId(), response.getDesc())));
        }
    }


}
