package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JFollowCmdActor extends Behaviors.MutableBehavior<JFollowCmdActor.FollowMessage> {


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
    }

    private ActorContext<FollowMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;


    private JFollowCmdActor(ActorContext<FollowMessage> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;


    }

    public static <FollowMessage> Behavior<FollowMessage> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<FollowMessage>) new JFollowCmdActor((ActorContext<JFollowCmdActor.FollowMessage>) ctx, commandResponseManager, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<FollowMessage> createReceive() {

        ReceiveBuilder<FollowMessage> builder = receiveBuilder()
                .onMessage(FollowCommandMessage.class,
                        message -> {
                            handleSubmitCommand(message);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    /**
     * This method process Follow command.
     * We assume all the validation have been done at ComponentHandler and CommandHandler.
     *
     * @param message
     */
    private void handleSubmitCommand(FollowCommandMessage message) {
        try {
            log.debug(()-> "Follow Command Message Received by FollowCmdActor in HCD " + message.controlCommand);
            Thread.sleep(500);
            //Serialize command data, submit to subsystem using ethernet ip connection
            log.debug(()-> "Got response from enc sussystem for follow command");
            message.replyTo.tell(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Completed(message.controlCommand.runId())));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


}
