package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.params.models.Prefix;
import csw.services.command.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.encsubsystem.enchcd.models.StartupCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;


public class JStartUpCmdActor extends MutableBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JStartUpCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new JStartUpCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, statePublisherActor,
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
                            log.debug(() -> "Starup Received");
                            handleStartupCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    private void handleStartupCommand(ControlCommand controlCommand) {
        log.debug(() -> "HCD handling startup command = " + controlCommand);
            StartupCommand.Response response = SimpleSimulator.getInstance().sendCommand(new StartupCommand());
            switch (response.getStatus()){
                case OK:
                    commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Completed(controlCommand.runId()));
                    statePublisherActor.tell(new JStatePublisherActor.InitializedMessage());
                    break;
                case ERROR:
                    commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Error(controlCommand.runId(), response.getDesc()));
            }
    }


}
