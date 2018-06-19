package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.ActorRef;
import akka.testkit.typed.javadsl.TestKitJunitResource;
import akka.testkit.typed.javadsl.TestProbe;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.Setup;
import csw.messages.params.models.Prefix;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

public class JFollowCmdActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    @Before
    public void setUp() throws Exception {
        System.out.println("test setup");
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("test tear down");
    }

    /**
     * given the HCD follow command actor is initialized, subsystem is also running
     * when valid follow message having follow command in it, is send
     * then it should reply with command successfully completed,
     *      state publisher actor should receive state change message.
     */
    @Test
    public void followCommandCompletion() {
        JLoggerFactory jLoggerFactory = new JLoggerFactory("enc-test-logger");
        TestProbe<JStatePublisherActor.StatePublisherMessage> statePublisherMessageTestProbe = testKit.createTestProbe();
        ActorRef<JFollowCmdActor.FollowMessage> followCmdActor = testKit.spawn(JFollowCmdActor.behavior(commandResponseManager, jLoggerFactory, statePublisherMessageTestProbe.getRef()));

        Setup setup = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        System.out.println("run id - " + setup.runId());
        TestProbe<JCommandHandlerActor.ImmediateResponseMessage> immediateResponseMessageTestProbe = testKit.createTestProbe();
        followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(setup, immediateResponseMessageTestProbe.getRef()));
        //checking if command completed message is received by test probe(replyTo actor)
        immediateResponseMessageTestProbe.expectMessage(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Completed(setup.runId())));
        //checking if statePublisher Actor received state change message
        statePublisherMessageTestProbe.expectMessage(new JStatePublisherActor.StateChangeMessage(Optional.empty(), Optional.of(JEncHcdHandlers.OperationalState.Following)));
    }
}