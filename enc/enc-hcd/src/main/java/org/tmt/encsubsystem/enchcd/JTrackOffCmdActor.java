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

public class JTrackOffCmdActor extends MutableBehavior<ControlCommand> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.


    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;


    private JTrackOffCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;


    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new JTrackOffCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, loggerFactory);
        });
    }

    /**
     * This method receives messages sent to actor.
     * based on message type it forward message to its dedicated handler method.
     * @return
     */
    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            handleSubmitCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    /**
     * This method process trackOff command.
     * We assume all the validation have been done at ComponentHandler and CommandHandler.
     *
     * @param message
     */
    private void handleSubmitCommand(ControlCommand message) {
        try {
            log.debug(() -> "TrackOff Command Message Received by TrackOffCmdActor in HCD " + message);
            Thread.sleep(500);
            //Serialize command data, submit to subsystem using ethernet ip connection
            log.debug(() -> "Got response from enc susbystem for trackOff command");
            commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Completed(message.runId()));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


}
