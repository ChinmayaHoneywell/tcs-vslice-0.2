package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;


public class JCommandHandlerActor extends MutableBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {
    }

    public static final class ImmediateResponseMessage implements CommandMessage {
        public final CommandResponse commandResponse;

        public ImmediateResponseMessage(CommandResponse commandResponse) {
            this.commandResponse = commandResponse;
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

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class GoOnlineMessage implements CommandMessage {
    }

    public static final class GoOfflineMessage implements CommandMessage {
    }

    public static final class UpdateTemplateHcdMessage implements CommandMessage {

        public final Optional<JCommandService> commandServiceOptional;

        public UpdateTemplateHcdMessage(Optional<JCommandService> commandServiceOptional) {
            this.commandServiceOptional = commandServiceOptional;
        }
    }

    private ActorContext<CommandMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private Boolean online;
    private CommandResponseManager commandResponseManager;
    private Optional<JCommandService> hcdCommandService;

    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, Boolean online, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.online = online;
        this.commandResponseManager = commandResponseManager;
        this.hcdCommandService = hcdCommandService;
    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, Boolean online, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, commandResponseManager, hcdCommandService, online, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("move"),
                        command -> {
                            log.debug(() -> "MoveMessage Received");
                            handleMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(ImmediateCommandMessage.class,
                        message -> message.controlCommand.commandName().name().equals("follow"),
                        message -> {
                            log.debug(() -> "Follow Message Received");
                            handleFollowCommand(message);
                            return Behaviors.same();
                        })
                .onMessage(GoOnlineMessage.class,
                        command -> {
                            log.debug(() -> "GoOnlineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, hcdCommandService, Boolean.TRUE, loggerFactory);
                        })
                .onMessage(UpdateTemplateHcdMessage.class,
                        command -> {
                            log.debug(() -> "UpdateTemplateHcdMessage Received");
                            // update the template hcd
                            return behavior(commandResponseManager, command.commandServiceOptional, online, loggerFactory);
                        })
                .onMessage(GoOfflineMessage.class,
                        command -> {
                            log.debug(() -> "GoOfflineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, hcdCommandService, Boolean.FALSE, loggerFactory);
                        });

        return builder.build();
    }

    private void handleSetTargetWavelengthCommand(ControlCommand controlCommand) {

        log.debug(() -> "handleSetTargetWavelengthCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> setTargetWavelengthCmdActor =
                    actorContext.spawnAnonymous(SetTargetWavelengthCmdActor.behavior(commandResponseManager, loggerFactory));

            setTargetWavelengthCmdActor.tell(controlCommand);

            // TODO: when the command is complete, kill the actor
            // ctx.stop(setTargetWavelengthCmdActor)
        }
    }


    private void handleMoveCommand(ControlCommand controlCommand) {

        log.debug(() -> "handleMoveCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> moveCmdActor =
                    actorContext.spawnAnonymous(MoveCmdActor.behavior(commandResponseManager, hcdCommandService, loggerFactory));

            moveCmdActor.tell(controlCommand);

        }
    }

    private void handleFollowCommand(ImmediateCommandMessage message) {
        log.debug(() -> "handleFollowCommand = " + message.controlCommand);
        if (online) {
            ActorRef<JFollowCmdActor.FollowMessage> followCmdActor = actorContext.spawnAnonymous(JFollowCmdActor.behavior(commandResponseManager, hcdCommandService, loggerFactory));
            followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));

        }
    }

}
