package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.*;

import akka.actor.typed.Props;
import akka.actor.typed.javadsl.ActorContext;
import csw.messages.commands.CommandName;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.models.Prefix;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JCommandHandlerActorTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JLoggerFactory jLoggerFactory;
    @Mock
    ILogger logger;


    TestInbox<JStatePublisherActor.StatePublisherMessage> statePublisherActorInbox;
    BehaviorTestKit<JCommandHandlerActor.CommandMessage> commandHandlerBehaviourKit;

    @Before
    public void setUp() throws Exception {
        when(jLoggerFactory.getLogger(isA(ActorContext.class),any())).thenReturn(logger);
        statePublisherActorInbox = TestInbox.create();
        commandHandlerBehaviourKit= BehaviorTestKit.create(JCommandHandlerActor.behavior(commandResponseManager, jLoggerFactory, statePublisherActorInbox.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given HCD is running,
     * when fastMove command as message is send to CommandHandlerActor,
     * then one Command Worker Actor (JFastMoveCmdActor) should be created
     *      and command should be send to newly created actor to process.
     */
    @Test
    public void handleFastMoveCommandTest() throws InterruptedException {

        Setup fastMoveSetupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("fastMove"), Optional.empty())
                .add(JKeyTypes.DoubleKey().make("az").set(2.64))
                .add(JKeyTypes.DoubleKey().make("el").set(5.34))
                .add(JKeyTypes.StringKey().make("mode").set("fast"))
                .add(JKeyTypes.StringKey().make("operation").set("On"));
        commandHandlerBehaviourKit.run(new JCommandHandlerActor.SubmitCommandMessage(fastMoveSetupCmd));
     //   commandHandlerBehaviourKit.expectEffect(Effects.spawnedAnonymous(JFastMoveCmdActor.behavior(commandResponseManager,jLoggerFactory, statePublisherActorInbox.getRef()),Props.empty()));
        TestInbox<ControlCommand> commandWorkerActorInbox =   commandHandlerBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(fastMoveSetupCmd);

    }
}