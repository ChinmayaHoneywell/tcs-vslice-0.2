package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.params.states.CurrentState;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

/**
 * This is an Actor Level Test.
 * <p>
 * Test case can be -
 * Handler level
 * Actor level
 * <p>
 * Actor Level -
 * Sending different message and checking if
 * it respond with a message
 * it create any child actor
 * it send message to any other actor
 * it changes its state
 * it crashes
 * <p>
 * Handler Level -
 * validation tests
 * failed validation and successful validation
 * <p>
 * command tests
 * test for immediate command
 * test for long running command
 */
public class JStatePublisherActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CurrentStatePublisher currentStatePublisher;

    @Captor
    ArgumentCaptor<CurrentState> currentStateArgumentCaptor;

    JLoggerFactory jLoggerFactory;
    //TestProbe<JStatePublisherActor.StatePublisherMessage> testProbe;
    ActorRef<JStatePublisherActor.StatePublisherMessage> statePublisherActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        statePublisherActor = testKit.spawn(JStatePublisherActor.behavior(currentStatePublisher, jLoggerFactory, JEncHcdHandlers.LifecycleState.Initialized, JEncHcdHandlers.OperationalState.Idle));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given hcd is initialized,
     * when state publisher actor received state change message
     * then it should publish change to assembly using current state publisher
     * <p>
     * <p>
     * Ref -
     * How to test if actor has executed some function?
     * https://stackoverflow.com/questions/27091629/akka-test-that-function-from-test-executed?answertab=oldest#tab-top
     */
    @Test
    public void stateChangeTest() throws InterruptedException {

        statePublisherActor.tell(new JStatePublisherActor.StateChangeMessage(Optional.of(JEncHcdHandlers.LifecycleState.Running), Optional.of(JEncHcdHandlers.OperationalState.Ready)));
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(currentStatePublisher).publish(currentStateArgumentCaptor.capture());
        CurrentState currentState = currentStateArgumentCaptor.getValue();
        assertEquals(currentState.stateName().name(), "OpsAndLifecycleState");
    }

    /**
     * given hcd is initialized,
     * when state publisher actor received publish message
     * then it should publish current position of enclosure using current state publisher
     */
    @Test
    public void currentStatePublishTest() throws InterruptedException {

        statePublisherActor.tell(new JStatePublisherActor.PublishMessage());
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(currentStatePublisher).publish(currentStateArgumentCaptor.capture());
        CurrentState currentState = currentStateArgumentCaptor.getValue();
        assertEquals(currentState.stateName().name(), "currentPosition");
    }
}