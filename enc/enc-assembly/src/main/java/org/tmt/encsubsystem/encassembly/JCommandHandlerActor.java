package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.ControlCommand;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;


public class JCommandHandlerActor extends Behaviors.MutableBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {}

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class GoOnlineMessage implements CommandMessage { }
    public static final class GoOfflineMessage implements CommandMessage { }

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
    private Optional<JCommandService> templateHcd;

    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> templateHcd, Boolean online, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.online = online;
        this.commandResponseManager = commandResponseManager;
        this.templateHcd = templateHcd;
    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> templateHcd, Boolean online, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, commandResponseManager, templateHcd, online, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("setTargetWavelength"),
                        command -> {
                            log.info("SetTargetWavelengthMessage Received");
                            handleSetTargetWavelengthCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("move"),
                        command -> {
                            log.info("MoveMessage Received");
                            handleMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("follow"),
                        command -> {
                            log.info("Follow Message Received");
                            handleFollowCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(GoOnlineMessage.class,
                        command -> {
                            log.info("GoOnlineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, templateHcd, Boolean.TRUE, loggerFactory);
                        })
                .onMessage(UpdateTemplateHcdMessage.class,
                        command -> {
                            log.info("UpdateTemplateHcdMessage Received");
                            // update the template hcd
                            return behavior(commandResponseManager, command.commandServiceOptional, online, loggerFactory);
                        })
                .onMessage(GoOfflineMessage.class,
                        command -> {
                            log.info("GoOfflineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, templateHcd, Boolean.FALSE, loggerFactory);
                        });

        return builder.build();
    }

    private void handleSetTargetWavelengthCommand(ControlCommand controlCommand) {

        log.info("handleSetTargetWavelengthCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> setTargetWavelengthCmdActor =
                    actorContext.spawnAnonymous(SetTargetWavelengthCmdActor.behavior(commandResponseManager, loggerFactory));

            setTargetWavelengthCmdActor.tell(controlCommand);

            // TODO: when the command is complete, kill the actor
            // ctx.stop(setTargetWavelengthCmdActor)
        }
    }



    private void handleMoveCommand(ControlCommand controlCommand) {

        log.info("handleMoveCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> moveCmdActor =
                    actorContext.spawnAnonymous(MoveCmdActor.behavior(commandResponseManager, templateHcd, loggerFactory));

            moveCmdActor.tell(controlCommand);

         }
    }

    private void handleFollowCommand(ControlCommand controlCommand) {

        log.info("handleFollowCommand = " + controlCommand);

        if (online) {

            ActorRef<ControlCommand> followCmdActor =
                    actorContext.spawnAnonymous(JFollowCmdActor.behavior(commandResponseManager, templateHcd, loggerFactory));

            followCmdActor.tell(controlCommand);

        }
    }


}
