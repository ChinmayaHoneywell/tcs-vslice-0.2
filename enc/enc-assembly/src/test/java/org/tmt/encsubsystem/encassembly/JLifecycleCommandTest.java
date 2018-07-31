package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.javadsl.ActorContext;
import csw.messages.commands.CommandName;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
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

import static org.mockito.Mockito.*;

public class JLifecycleCommandTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    private IConfigClientService configClientApi;

    @Mock
    JCommandService hcdCommandService;
    TestInbox<JCommandHandlerActor.CommandMessage> commandHandlerActor;

    @Mock
    JLoggerFactory jLoggerFactory;
    @Mock
    ILogger logger;

    BehaviorTestKit<JLifecycleActor.LifecycleMessage> lifecycleBehaviourKit;


    @Before
    public void setUp() throws Exception {
        when(jLoggerFactory.getLogger(isA(ActorContext.class),any())).thenReturn(logger);
        commandHandlerActor = TestInbox.create();
        lifecycleBehaviourKit= BehaviorTestKit.create(JLifecycleActor.behavior(commandResponseManager, Optional.of(hcdCommandService), configClientApi,commandHandlerActor.getRef(), jLoggerFactory));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given assembly is initialized,
     * when startup command as message is send to LifecycleActor,
     * then one Command Worker Actor (JStartUpCmdActor) should be created
     *      and command should be send to newly created actor to process.
     */
    @Test
    public void handleStartupCommandTest() throws InterruptedException {
        Setup startupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("startup"), Optional.empty());
        lifecycleBehaviourKit.run(new JLifecycleActor.SubmitCommandMessage(startupCmd));
        TestInbox<ControlCommand> commandWorkerActorInbox =   lifecycleBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(startupCmd);


    }

    /**
     * given assembly is initialized,
     * when shutdown command as message is send to LifecycleActor,
     * then one Command Worker Actor (JShutdownCmdActor) should be created
     *      and command should be send to newly created actor to process.
     */
    @Test
    public void handleShutdownCommandTest()  {
        Setup shutdownCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("shutdown"), Optional.empty());
        lifecycleBehaviourKit.run(new JLifecycleActor.SubmitCommandMessage(shutdownCmd));
        TestInbox<ControlCommand> commandWorkerActorInbox =   lifecycleBehaviourKit.childInbox("$a");
        TestInbox<ControlCommand> controlCommandTestInbox = commandWorkerActorInbox.expectMessage(shutdownCmd);

    }
}