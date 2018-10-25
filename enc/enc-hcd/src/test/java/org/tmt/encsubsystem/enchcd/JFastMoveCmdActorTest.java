package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.models.Prefix;
import csw.services.command.CommandResponseManager;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.mockito.Mockito.verify;

public class JFastMoveCmdActorTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;

    JLoggerFactory jLoggerFactory;
    TestProbe<JStatePublisherActor.StatePublisherMessage> statePublisherMessageTestProbe;
    ActorRef<ControlCommand> fastMoveCmdActor;

    private Key<Double> azKey = JKeyTypes.DoubleKey().make("az");
    private Key<Double> elKey = JKeyTypes.DoubleKey().make("el");
    private Key<String> mode = JKeyTypes.StringKey().make("mode");
    private Key<String> operation = JKeyTypes.StringKey().make("operation");

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        statePublisherMessageTestProbe = testKit.createTestProbe();
        fastMoveCmdActor = testKit.spawn(JFastMoveCmdActor.behavior(commandResponseManager, jLoggerFactory, statePublisherMessageTestProbe.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the HCD fastMove command actor is initialized, subsystem is also running
     * when message having valid fastMove command in it, is send
     * then it should  update command response manager that command successfully completed
     */
    @Test
    public void fastMoveCommandCompletion() {

        Setup setup = new Setup(new Prefix("enc.enc-test"), new CommandName("fastMove"), Optional.empty())
                .add(azKey.set(2.60))
                .add(elKey.set(1.4))
                .add(mode.set("fast"))
                .add(operation.set("On"));
        fastMoveCmdActor.tell(setup);
        verify(commandResponseManager).addOrUpdateCommand(setup.runId(), new CommandResponse.Completed(setup.runId()));
    }
}