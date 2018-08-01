package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JStartUpCmdActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JCommandService hcdCommandService;

    JLoggerFactory jLoggerFactory;
    ActorRef<ControlCommand> startUpCmdActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        startUpCmdActor = testKit.spawn(JStartUpCmdActor.behavior(commandResponseManager, Optional.of(hcdCommandService), jLoggerFactory));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is initialized, subsystem is initialized
     * when valid startup command is send to command worker actor
     * then worker actor submit command to HCD and update command response in command response manager.
     */

    @Test
    public void startupCommandCompletion() throws InterruptedException {

        Setup startupCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("startup"), Optional.empty());
        when(hcdCommandService.submitAndSubscribe(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(startupCmd.runId())));
        startUpCmdActor.tell(startupCmd);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addOrUpdateCommand(startupCmd.runId(), new CommandResponse.Completed(startupCmd.runId()));
    }
}