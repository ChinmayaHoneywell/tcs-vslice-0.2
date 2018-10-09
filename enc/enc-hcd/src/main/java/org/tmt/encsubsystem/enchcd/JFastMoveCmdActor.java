package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
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
import org.tmt.encsubsystem.enchcd.simplesimulator.FastMoveCommand;
import org.tmt.encsubsystem.enchcd.simplesimulator.SimpleSimulator;

import java.util.Optional;

public class JFastMoveCmdActor extends MutableBehavior<ControlCommand> {

    private ActorContext<ControlCommand> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private CommandResponseManager commandResponseManager;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;


    private JFastMoveCmdActor(ActorContext<ControlCommand> actorContext, CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.commandResponseManager = commandResponseManager;
        this.statePublisherActor = statePublisherActor;
    }

    public static <ControlCommand> Behavior<ControlCommand> behavior(CommandResponseManager commandResponseManager, JLoggerFactory loggerFactory, ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<ControlCommand>) new JFastMoveCmdActor((ActorContext<csw.messages.commands.ControlCommand>) ctx, commandResponseManager, loggerFactory, statePublisherActor);
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
                            log.debug(() -> "FastMove Command Message Received");
                            handleSubmitCommand(command);
                            return Behaviors.stopped();
                        });
        return builder.build();
    }

    /**
     * Submitting command to ENC Control system, once subsystem respond then sending command response on command response manager.
     * Updating Operational state to state publisher actor.
     *
     * @param message
     */
    private void handleSubmitCommand(ControlCommand message) {
        System.out.println("worker actor handling command fast move");
        Parameter azParam = message.paramSet().find(x -> x.keyName().equals("az")).get();
        Parameter elParam = message.paramSet().find(x -> x.keyName().equals("el")).get();
            log.debug(() -> "Submitting fastMove command to ENC Subsystem");
           FastMoveCommand.Response response = SimpleSimulator.getInstance().sendCommand(new FastMoveCommand((double)azParam.value(0), (double)elParam.value(0)));
           switch (response.getStatus()){
               case OK:
                   commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Completed(message.runId()));
                   statePublisherActor.tell(new JStatePublisherActor.StateChangeMessage(Optional.empty(), Optional.of(JEncHcdHandlers.OperationalState.InPosition)));
                   break;
               case ERROR:
                   commandResponseManager.addOrUpdateCommand(message.runId(), new CommandResponse.Error(message.runId(), response.getDesc()));
           }
    }


}
