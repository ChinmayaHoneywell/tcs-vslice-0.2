package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.params.generics.Parameter;
import csw.services.command.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.tcs.pk.wrapper.TpkWrapper;

public class SetTargetCmdActor extends MutableBehavior<ControlCommand> {

    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;

    private TpkWrapper tpkWrapper;

    private SetTargetCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, TpkWrapper tpkWrapper) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.tpkWrapper = tpkWrapper;
    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, TpkWrapper tpkWrapper) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new SetTargetCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager,
                    loggerFactory, tpkWrapper);
        });
    }


    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.info("Inside SetTargetCmdActor: SetTargetCmd Received");
                            handleSubmitCommand(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        log.info("Inside SetTargetCmdActor: handleSubmitCommand start");

        Parameter raParam = message.paramSet().find(x -> x.keyName().equals("ra")).get();
        Parameter decParam = message.paramSet().find(x -> x.keyName().equals("dec")).get();

        Double ra = (Double) raParam.value(0);
        Double dec = (Double) decParam.value(0);

        log.info("Inside SetTargetCmdActor: handleSubmitCommand: ra is: " + ra + ": dec is: " + dec);

        tpkWrapper.newTarget(ra, dec);

        commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Completed(message.runId()));

        log.info("Inside SetTargetCmdActor: command message handled");
    }

}
