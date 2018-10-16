package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.util.Timeout;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.generics.Parameter;
import csw.messages.params.models.Id;
import csw.messages.params.models.ObsId;
import csw.messages.params.models.Prefix;
import csw.services.command.CommandResponseManager;
import csw.services.command.javadsl.JCommandService;

import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class MoveCmdActor extends MutableBehavior<ControlCommand> {


    // Add messages here
    // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.


    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    private Optional<JCommandService> hcdCommandService;


    private MoveCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.hcdCommandService = hcdCommandService;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, Optional<JCommandService> hcdCommandService, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new MoveCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, hcdCommandService,
                    loggerFactory);
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
                            log.debug(() -> "Move Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();// actor stops itself, it is meant to only process one command.
                        });
        return builder.build();
    }

    private void handleSubmitCommand(ControlCommand message) {

        // NOTE: we use get instead of getOrElse because we assume the command has been validated
        //Parameter axesParam = message.paramSet().find(x -> x.keyName().equals("axes")).get();
        Parameter operation = message.paramSet().find(x -> x.keyName().equals("operation")).get();
        Parameter baseParam = message.paramSet().find(x -> x.keyName().equals("base")).get();
        Parameter capParam = message.paramSet().find(x -> x.keyName().equals("cap")).get();
        Parameter mode = message.paramSet().find(x -> x.keyName().equals("mode")).get();
        Parameter timeDuration = message.paramSet().find(x -> x.keyName().equals("timeDuration")).get();

        CompletableFuture<CommandResponse> moveFuture = move(message.maybeObsId(), operation, baseParam, capParam, mode, timeDuration);

        moveFuture.thenAccept((response) -> {

            log.debug(() -> "response = " + response);
            log.debug(() -> "runId = " + message.runId());

            commandResponseManager.addSubCommand(message.runId(), response.runId());

            commandResponseManager.updateSubCommand(response.runId(), response);

            log.debug(() -> "move command message handled");


        });


    }

    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    CompletableFuture<CommandResponse> move(Option<ObsId> obsId,
                                            Parameter operation,
                                            Parameter baseParam,
                                            Parameter capParam,
                                            Parameter mode,
                                            Parameter timeDuration) {
        String modeValue = (String) mode.get(0).get();
        if (hcdCommandService.isPresent()) {
            log.debug(() -> "Mode - " + modeValue);
            if ("fast".equals(modeValue)) {
                log.debug(() -> "Submitting fastMove command to HCD");
                Setup fastMoveSetupCmd = new Setup(templateHcdPrefix, new CommandName("fastMove"), Optional.empty())
                        .add(baseParam)
                        .add(capParam)
                        .add(mode)
                        .add(operation);


                CompletableFuture<CommandResponse> commandResponse = hcdCommandService.get()
                        .submitAndSubscribe(
                                fastMoveSetupCmd,
                                Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS))
                        );

                return commandResponse;

            } else {

                return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Invalid mode value"));
            }

        } else {
            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate HCD"));

        }
    }
}
