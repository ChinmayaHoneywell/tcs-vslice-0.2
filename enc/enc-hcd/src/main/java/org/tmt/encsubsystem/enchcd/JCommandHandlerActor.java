package org.tmt.encsubsystem.enchcd;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.ControlCommand;

import csw.services.command.scaladsl.CommandResponseManager;

import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JCommandHandlerActor extends Behaviors.MutableBehavior<JCommandHandlerActor.CommandMessage> {


    // add messages here
    interface CommandMessage {}

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }


    private ActorContext<CommandMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    private CommandResponseManager commandResponseManager;


    private JCommandHandlerActor(ActorContext<CommandMessage> actorContext, CommandResponseManager commandResponseManager,  JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

        this.commandResponseManager = commandResponseManager;

    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<CommandMessage>) new JCommandHandlerActor((ActorContext<JCommandHandlerActor.CommandMessage>) ctx, commandResponseManager, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("fastMove"),
                        command -> {
                            log.info("FastMove Message Received");
                            handleFastMoveCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("trackOff"),
                        command -> {
                            log.info("trackOff Received");
                            handleTrackOffCommand(command.controlCommand);
                            return Behaviors.same();
                        });

        return builder.build();
    }

    private void handleFastMoveCommand(ControlCommand controlCommand) {

        log.info("HCD handling fastMove command = " + controlCommand);

            ActorRef<ControlCommand> fastMoveCmdActor =
                    actorContext.spawnAnonymous(JFastMoveCmdActor.behavior(commandResponseManager, loggerFactory));

        fastMoveCmdActor.tell(controlCommand);

            // TODO: when the command is complete, kill the actor
            // ctx.stop(setTargetWavelengthCmdActor)

    }


    private void handleTrackOffCommand(ControlCommand controlCommand) {

        log.info("HCD handling trackOff command = " + controlCommand);


            ActorRef<ControlCommand> trackOffCmdActor =
                    actorContext.spawnAnonymous(JTrackOffCmdActor.behavior(commandResponseManager, loggerFactory));

        trackOffCmdActor.tell(controlCommand);


    }


}
