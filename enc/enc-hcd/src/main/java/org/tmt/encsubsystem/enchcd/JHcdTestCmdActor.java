package org.tmt.encsubsystem.enchcd;

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

/**
 * This is a CommandWorkerActor for HcdTestCommand.
 * HcdTestCommand is a dummy command created for generating performance measures
 */
public class JHcdTestCmdActor extends MutableBehavior<ControlCommand> {
    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;

    private JHcdTestCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new JHcdTestCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, loggerFactory);
        });
    }

    /**
     * This method receives messages sent to this worker actor.
     * @return
     */
    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(() -> "HcdTestCommand Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    /**
     * This method process the command.
     * this actor receives HcdTestCommand which is just a dummy command for performance testing,
     * This method will send CommandResponse to assembly using CommandResponseManager
     * @param controlCommand
     */
    private void handleSubmitCommand(ControlCommand controlCommand) {
        commandResponseManager.addOrUpdateCommand(controlCommand.runId(),  new CommandResponse.Completed(controlCommand.runId()));
    }


}
