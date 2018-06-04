package org.tmt.encsubsystem.encassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import akka.util.Timeout;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.javadsl.JUnits;
import csw.messages.params.generics.Parameter;
import csw.messages.params.models.Id;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Assembly's Follow Command Actor.
 * This Actor submit follow command from assembly to hcd.
 */
public class JFollowCmdActor extends Behaviors.MutableBehavior<ControlCommand> {


        // Add messages here
        // No sealed trait/interface or messages for this actor.  Always accepts the Submit command message.

        private ActorContext<ControlCommand> actorContext;
        private JLoggerFactory loggerFactory;
        private ILogger log;
        private CommandResponseManager commandResponseManager;
        private Optional<JCommandService> templateHcd;

    private Prefix encAssemblyPrefix = new Prefix("tcs.encA");


        private JFollowCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, Optional<JCommandService> templateHcd, JLoggerFactory loggerFactory) {
            this.actorContext = actorContext;
            this.loggerFactory = loggerFactory;
            this.log = loggerFactory.getLogger(actorContext, getClass());
            this.commandResponseManager = commandResponseManager;
            this.templateHcd = templateHcd;


        }

        public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager,Optional<JCommandService> templateHcd, JLoggerFactory loggerFactory) {
            return Behaviors.setup(ctx -> {
                return (Behaviors.MutableBehavior<ControlCommand>) new JFollowCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager,templateHcd, loggerFactory);
            });
        }


        @Override
        public Behaviors.Receive<ControlCommand> createReceive() {

            ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                    .onMessage(ControlCommand.class,
                            command -> {
                                log.info("Follow Command Message Received by FollowCmdActor in Assembly");
                                handleSubmitCommand(command);
                                return Behaviors.same();
                            });
            return builder.build();
        }

    /**
     * This method handle follow/Track command.
     * Based on parameter received it submit any of the below three command to hcd.
     * 1. TrackOff
     * 2. FastMoveToTrack <-TODO
     * 3. SmoothMoveToTrack <-TODO
     * @param message
     */
        private void handleSubmitCommand(ControlCommand message) {
            // NOTE: we use get instead of getOrElse because we assume the command has been validated
            Parameter operation = message.paramSet().find(x -> x.keyName().equals("operation")).get();
            String operationValue = (String) operation.get(0).get();
            Parameter azParam = message.paramSet().find(x -> x.keyName().equals("az")).get();
            Parameter elParam = message.paramSet().find(x -> x.keyName().equals("el")).get();
            Parameter mode = message.paramSet().find(x-> x.keyName().equals("mode")).get();
            Parameter timeDuration = message.paramSet().find(x-> x.keyName().equals("timeDuration")).get();

            if(templateHcd.isPresent()){
                log.debug("Operation - " + operationValue);
                if("Off".equals(operationValue)){

                    Setup setup = new Setup(encAssemblyPrefix, new CommandName("trackOff"), Optional.empty());
                    templateHcd.get()
                            .submitAndSubscribe(setup,  Timeout.durationToTimeout(FiniteDuration.apply(10, TimeUnit.SECONDS)))
                            .thenAccept(response->{
                                commandResponseManager.addSubCommand(message.runId(), response.runId());
                                commandResponseManager.updateSubCommand(response.runId(), response);
                            });
                }else{
                    commandResponseManager.addOrUpdateCommand(message.runId(),new CommandResponse.Error(message.runId(), "Invalid Operation for follow command"));
                }
            } else {
                 commandResponseManager.addOrUpdateCommand(message.runId(),new CommandResponse.Error(message.runId(), "Can't locate TcsEncHcd"));
            }
        }





}
