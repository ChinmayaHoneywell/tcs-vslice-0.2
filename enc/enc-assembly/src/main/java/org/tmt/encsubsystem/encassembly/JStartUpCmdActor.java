package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.util.Timeout;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


public class JStartUpCmdActor extends Behaviors.MutableBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private Optional<JCommandService> hcdCommandService;


    private JStartUpCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.hcdCommandService = hcdCommandService;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<ControlCommand>) new JStartUpCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, hcdCommandService,
                    loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug("Starup Received");
                            handleSubmitCommand(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        if (hcdCommandService.isPresent()) {
            log.debug("Submitting startup command from assembly to hcd");
            hcdCommandService.get()
                    .submitAndSubscribe(
                            message,
                            Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                    ).thenAccept(response -> {
                log.debug("received response from hcd");
                commandResponseManager.addOrUpdateCommand(message.runId(), response);

            });

        } else {
            //
            commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Error(message.runId(), "Can't locate HCD"));

        }
    }


}
