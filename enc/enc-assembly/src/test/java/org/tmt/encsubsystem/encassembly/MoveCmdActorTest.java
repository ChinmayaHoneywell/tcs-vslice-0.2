package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.models.Id;
import csw.services.command.CommandResponseManager;
import csw.services.command.javadsl.JCommandService;
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

public class MoveCmdActorTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JCommandService hcdCommandService;

    JLoggerFactory jLoggerFactory;
    ActorRef<ControlCommand> moveCmdActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        moveCmdActor = testKit.spawn(MoveCmdActor.behavior(commandResponseManager, Optional.of(hcdCommandService), jLoggerFactory));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is running,
     * when valid move command is send to command worker actor
     * then worker actor create and submit sub command to HCD,
     * join sub command with actual command
     * and update command response in command response manager.
     */

    @Test
    public void startupCommandCompletion() throws InterruptedException {
        Id responseId = new Id("");
        Setup moveCommand = TestConstants.moveCommand();
        when(hcdCommandService.submitAndSubscribe(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(responseId)));
        moveCmdActor.tell(moveCommand);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addSubCommand(moveCommand.runId(), responseId);
        verify(commandResponseManager).updateSubCommand(responseId, new CommandResponse.Completed(responseId));
    }
}