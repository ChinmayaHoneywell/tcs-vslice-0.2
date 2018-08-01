package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
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
import static org.mockito.Mockito.when;

public class JFollowCmdActorTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    JCommandService hcdCommandService;

    JLoggerFactory jLoggerFactory;
    ActorRef<JFollowCmdActor.FollowMessage> followCmdActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        followCmdActor = testKit.spawn(JFollowCmdActor.behavior(commandResponseManager, Optional.of(hcdCommandService), jLoggerFactory));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the Assembly is running,
     * when valid follow command is send to command worker actor
     * then worker actor submit command to HCD,
     * and response is send back sender immediately.
     */
    @Test
    public void followCommandCompletion() {
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        when(hcdCommandService.submitAndSubscribe(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(followCommand.runId())));
        TestProbe<JCommandHandlerActor.ImmediateResponseMessage> responseTestProbe = testKit.createTestProbe();
        followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(followCommand, responseTestProbe.getRef()));
        responseTestProbe.expectMessage(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Completed(followCommand.runId())));
    }
}