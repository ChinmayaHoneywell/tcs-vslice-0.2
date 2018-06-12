package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JFastMoveCmdActor extends Behaviors.MutableBehavior<ControlCommand> {

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;


    private JFastMoveCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;


    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<ControlCommand>) new JFastMoveCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug("FastMove Command Message Received");
                            handleSubmitCommand(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        //  Parameter axesParam = message.paramSet().find(x -> x.keyName().equals("axes")).get();
        try {
            log.debug("Submitting fastMove command to ENC Subsystem");
            Thread.sleep(500);
            //Serialize command data, submit to subsystem using ethernet ip connection
            commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Completed(message.runId()));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


}
