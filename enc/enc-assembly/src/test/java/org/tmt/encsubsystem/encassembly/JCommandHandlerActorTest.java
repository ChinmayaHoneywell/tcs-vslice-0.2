package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.typed.javadsl.ActorContext;
import csw.messages.commands.CommandName;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.javadsl.JUnits;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

public class JCommandHandlerActorTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JLoggerFactory jLoggerFactory;
    @Mock
    ILogger logger;

    @Mock
    JCommandService hcdCommandService;

    BehaviorTestKit<JCommandHandlerActor.CommandMessage> commandHandlerBehaviourKit;

    TestInbox<JCommandHandlerActor.ImmediateResponseMessage> replyTo;

    @Before
    public void setUp() throws Exception {
        when(jLoggerFactory.getLogger(isA(ActorContext.class), any())).thenReturn(logger);
        replyTo = TestInbox.create();
        commandHandlerBehaviourKit = BehaviorTestKit.create(JCommandHandlerActor.behavior(commandResponseManager, Optional.of(hcdCommandService), true, jLoggerFactory, Optional.empty()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given Assembly is running,
     * when move command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (MoveCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleMoveCommandTest() {
        Long[] timeValue = new Long[1];
        timeValue[0] = 10L;
        Setup moveSetupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyTypes.DoubleKey().make("az").set(2.64))
                .add(JKeyTypes.DoubleKey().make("el").set(5.34))
                .add(JKeyTypes.StringKey().make("mode").set("fast"))
                .add(JKeyTypes.StringKey().make("operation").set("On"))
                .add(JKeyTypes.LongKey().make("timeDuration").set(timeValue, JUnits.second));
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(moveSetupCmd));
        //   commandHandlerBehaviourKit.expectEffect(Effects.spawnedAnonymous(JFastMoveCmdActor.behavior(commandResponseManager,jLoggerFactory, statePublisherActorInbox.getRef()),Props.empty()));
        TestInbox<ControlCommand> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(moveSetupCmd);

    }


    /**
     * given Assembly is running,
     * when follow command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JFollowCmdActor) should be created
     * and command should be send to newly created actor to process.
     */
    @Test
    public void handleFollowCommandTest() {
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        JCommandHandlerActor.ImmediateCommandMessage message = new JCommandHandlerActor.ImmediateCommandMessage(followCommand, replyTo.getRef());
        commandHandlerBehaviourKit.run(message);
        TestInbox<JFollowCmdActor.FollowMessage> commandWorkerActorInbox = commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<JFollowCmdActor.FollowMessage> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(new JFollowCmdActor.FollowCommandMessage(message.controlCommand, message.replyTo));

    }
}