package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.models.Prefix;
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

public class JShutdownCmdActorTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JCommandService hcdCommandService;

    JLoggerFactory jLoggerFactory;
    ActorRef<ControlCommand> shutdownCmdActor;
    TestProbe<JMonitorActor.MonitorMessage> monitorActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        monitorActor= testKit.createTestProbe();
        shutdownCmdActor = testKit.spawn(JStartUpCmdActor.behavior(commandResponseManager, Optional.of(hcdCommandService), jLoggerFactory, monitorActor.getRef()));

    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is running, subsystem is running
     * when valid shutdown command is send to command worker actor
     * then worker actor submit command to HCD and update command response in command response manager.
     */
    @Test
    public void shutdownCommandCompletion() throws InterruptedException {

        Setup shutdownCmd = new Setup(new Prefix("enc.enc-test"), new CommandName("shutdown"), Optional.empty());
        when(hcdCommandService.submitAndSubscribe(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(shutdownCmd.runId())));
        shutdownCmdActor.tell(shutdownCmd);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addOrUpdateCommand(shutdownCmd.runId(), new CommandResponse.Completed(shutdownCmd.runId()));
    }
}