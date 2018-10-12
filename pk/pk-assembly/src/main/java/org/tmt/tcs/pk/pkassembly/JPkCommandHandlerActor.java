package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.ControlCommand;
import csw.services.command.CommandResponseManager;
import org.tmt.tcs.pk.pkassembly.JPkCommandHandlerActor.CommandMessage;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.tmt.tcs.pk.wrapper.TpkWrapper;

public class JPkCommandHandlerActor extends MutableBehavior<CommandMessage> {


    // add messages here
    interface CommandMessage {}

    public static final class SubmitCommandMessage implements CommandMessage {

        public final ControlCommand controlCommand;


        public SubmitCommandMessage(ControlCommand controlCommand) {
            this.controlCommand = controlCommand;
        }
    }

    public static final class GoOnlineMessage implements CommandMessage { }
    public static final class GoOfflineMessage implements CommandMessage { }

    private ActorContext<CommandMessage> actorContext;
    private JLoggerFactory loggerFactory;
    private ILogger log;
    private Boolean online;
    private CommandResponseManager commandResponseManager;
    private TpkWrapper tpkWrapper;

    private JPkCommandHandlerActor(ActorContext<CommandMessage> actorContext, CommandResponseManager commandResponseManager, Boolean online, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());
        this.online = online;
        this.commandResponseManager = commandResponseManager;

        initiateTpkEndpoint();
    }

    public static <CommandMessage> Behavior<CommandMessage> behavior(CommandResponseManager commandResponseManager, Boolean online, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<CommandMessage>) new JPkCommandHandlerActor((ActorContext<JPkCommandHandlerActor.CommandMessage>) ctx, commandResponseManager, online, loggerFactory);
        });
    }


    @Override
    public Behaviors.Receive<CommandMessage> createReceive() {

        ReceiveBuilder<CommandMessage> builder = receiveBuilder()
                .onMessage(SubmitCommandMessage.class,
                        command -> command.controlCommand.commandName().name().equals("setTarget"),
                        command -> {
                            log.info("Inside JPkCommandHandlerActor: SetTargetMessage Received");
                            handleSetTargetCommand(command.controlCommand);
                            return Behaviors.same();
                        })
                .onMessage(GoOnlineMessage.class,
                        command -> {
                            log.info("Inside JPkCommandHandlerActor: GoOnlineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, Boolean.TRUE, loggerFactory);
                        })
                .onMessage(GoOfflineMessage.class,
                        command -> {
                            log.info("Inside JPkCommandHandlerActor: GoOfflineMessage Received");
                            // change the behavior to online
                            return behavior(commandResponseManager, Boolean.FALSE, loggerFactory);
                        });

        return builder.build();
    }

    private void handleSetTargetCommand(ControlCommand controlCommand) {

        log.info("Inside JPkCommandHandlerActor: handleSetTargetCommand = " + controlCommand);

        if (online) {
            ActorRef<ControlCommand> setTargetCmdActor =
                    actorContext.spawnAnonymous(SetTargetCmdActor.behavior(commandResponseManager, loggerFactory, tpkWrapper));

            setTargetCmdActor.tell(controlCommand);

        }
    }

    /**
     * This helps in initializing TPK JNI Wrapper in separate thread, so that
     * New Target and Offset requests can be passed on to it
     */
    public void initiateTpkEndpoint() {
        log.debug("Inside JPkCommandHandlerActor: initiateTpkEndpoint");

        System.loadLibrary("example");

        tpkWrapper = new TpkWrapper();

        new Thread(new Runnable() {
            public void run() {
                tpkWrapper.initiate();
            }
        }).start();

        try {
            Thread.sleep(100, 0);
        } catch (InterruptedException e) {
            log.error("Inside TpkCommandHandler: initiateTpkEndpoint: Error is: " + e);
        }
    }

}
