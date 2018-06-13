package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.params.models.Prefix;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;


public class JShutdownCmdActor extends Behaviors.MutableBehavior<ControlCommand> {


    private Prefix templateHcdPrefix = new Prefix("tcs.encA");

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JShutdownCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;

    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (Behaviors.MutableBehavior<ControlCommand>) new JShutdownCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, statePublisherActor,
                    loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<ControlCommand> createReceive() {

        ReceiveBuilder<ControlCommand> builder = receiveBuilder()
                .onMessage(ControlCommand.class,
                        command -> {
                            log.debug(()-> "Shutdown Received");
                            handleShutdownCommand(command);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void handleShutdownCommand(ControlCommand controlCommand) {

        log.debug(()-> "HCD handling shutdown command = " + controlCommand);
        log.debug(()-> "TODO: should park enc and the disconnet with subsystem");

        try {
            Thread.sleep(500);
            //Serialize command data, submit to subsystem using ethernet ip connection
            commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Completed(controlCommand.runId()));
            // get subsystem state from command response and tell state publisher about changed state.
            statePublisherActor.tell(new JStatePublisherActor.StateChangeMessage(Optional.of(JEncHcdHandlers.LifecycleState.Initialized), Optional.of(JEncHcdHandlers.OperationalState.Idle)));

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


}
