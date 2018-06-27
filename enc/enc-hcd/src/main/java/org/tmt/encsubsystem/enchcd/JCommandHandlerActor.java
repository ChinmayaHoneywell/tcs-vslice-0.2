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

public class JCommandHandlerActor extends Behaviors.MutableBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {
    }

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }


    }

    public static final class ImmediateCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;
        public final ActorRef<ImmediateResponseMessage> replyTo;


        public ImmediateCommandMessage(ControlCommand controlCommand, ActorRef<ImmediateResponseMessage> replyTo) {
            this.controlCommand = controlCommand;
            this.replyTo = replyTo;
        }
    }

    public static final class ImmediateResponseMessage implements CommandMessage {
        public final CommandResponse commandResponse;

        public ImmediateResponseMessage(CommandResponse commandResponse) {
            this.commandResponse = commandResponse;
        }

        @Override
        public boolean equals(Object obj) {

            if (!(obj instanceof ImmediateResponseMessage)) {
                return false;
            }
            boolean isSame = commandResponse.equals(((ImmediateResponseMessage) obj).commandResponse);
            return isSame;
        }
    }


    private ActorContext<CommandMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    private CommandResponseManager commandResponseManager;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;

    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, commandResponseManager, loggerFactory, statePublisherActor);
        });
    }


    @Override
    public Behaviors.Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()

                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("fastMove"),
                        command -> {
                            log.debug(() -> "FastMove Message Received");
                            handleFastMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("trackOff"),
                        command -> {
                            log.debug(() -> "trackOff Received");
                            handleTrackOffCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(ImmediateCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("follow"),
                        message -> {
                            log.debug(() -> "follow command received");
                            handleFollowCommand(message);
                            return Behaviors.same();
                        });


        return builder.build();
    }


    private void handleFastMoveCommand(ControlCommand controlCommand) {

        log.debug(() -> "HCD handling fastMove command = " + controlCommand);

        ActorRef<ControlCommand> fastMoveCmdActor =
                actorContext.spawnAnonymous(JFastMoveCmdActor.behavior(commandResponseManager, loggerFactory, statePublisherActor));

        fastMoveCmdActor.tell(controlCommand);

        // TODO: when the command is complete, kill the actor
        // ctx.stop(setTargetWavelengthCmdActor)

    }


    private void handleTrackOffCommand(ControlCommand controlCommand) {

        log.debug(() -> "HCD handling trackOff command = " + controlCommand);


        ActorRef<ControlCommand> trackOffCmdActor =
                actorContext.spawnAnonymous(JTrackOffCmdActor.behavior(commandResponseManager, loggerFactory));

        trackOffCmdActor.tell(controlCommand);


    }

    private void handleFollowCommand(ImmediateCommandMessage message) {

        log.debug(() -> "HCD handling follow command = " + message.controlCommand);


        ActorRef<JFollowCmdActor.FollowMessage> followCmdActor =
                actorContext.spawnAnonymous(JFollowCmdActor.behavior(commandResponseManager, loggerFactory, statePublisherActor));

        followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));


    }


}
