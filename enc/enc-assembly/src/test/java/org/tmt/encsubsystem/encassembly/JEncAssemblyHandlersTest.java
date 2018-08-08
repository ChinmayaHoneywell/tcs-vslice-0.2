package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandIssue;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.Setup;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.*;
import csw.messages.params.models.Prefix;
import csw.messages.params.states.CurrentState;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.command.scaladsl.CurrentStateSubscription;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.event.javadsl.IEventService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.swing.text.html.Option;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * Tests in this class are Asynchronous. All Actor are created using akka test kit.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({JConfigClientFactory.class, AkkaLocation.class, Optional.class})
public class JEncAssemblyHandlersTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Captor
    private ArgumentCaptor<Consumer<CurrentState>> captor;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();
    @Mock
    ActorContext<TopLevelActorMessage> ctx;

    ComponentInfo componentInfo;

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    CurrentStatePublisher currentStatePublisher;
    @Mock
    CurrentStateSubscription currentStateSubscription;

    @Mock
    ILocationService locationService;

    @Mock
    JCommandService hcdService;
  //  Optional<JCommandService> enclowCommandOpt;

    @Mock
    IEventService eventService;

    @Mock
    IConfigClientService configClientApi;

    @Mock
    Connection connection;

    AkkaLocation location;

    JLoggerFactory jLoggerFactory;

    JEncAssemblyHandlers assemblyHandlers;


    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        location = PowerMockito.mock(AkkaLocation.class);
        PowerMockito.mockStatic(JConfigClientFactory.class);
        when(JConfigClientFactory.clientApi(any(), any())).thenReturn(configClientApi);
        when(ctx.getSystem()).thenReturn(testKit.system());
        when(ctx.spawnAnonymous(any(Behavior.class))).thenAnswer(i->{
            return testKit.spawn(i.getArgument(0));
        });
        JEncAssemblyBehaviorFactory factory = new JEncAssemblyBehaviorFactory();
        assemblyHandlers = (JEncAssemblyHandlers)factory.jHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, jLoggerFactory);
    }

    @After
    public void tearDown() {
    }

    /**
     * State Validation(Move command) - Given assembly is in idle state,
     * when move command is submitted
     * then command should fail and invalid state command response should be returned.
     */
    @Test
    public void stateValidationTest(){
        Setup moveCommand = TestConstants.moveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse, new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state")));
    }


    /**
        Parameter Validation Test(Move Command ) - Given assembly is in ready to accept commands,
        when invalid move command is submitted
        then command should be rejected
     */
    @Test
    public void parameterValidationTest(){
        Setup moveCommand = TestConstants.invalidMoveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse,  new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.MissingKeyIssue("Move command is missing mode parameter")));
    }

    /**
     * Faulted State (HCD Connection issue) Test - Given Assembly is in ready state,
     * when connection to hcd become unavailable,
     * then submitted command should fail due to faulted state issue.
     */

    @Test
    public void hcdConnectionFaultedTest(){

        Setup moveCommand = TestConstants.moveCommand();

        assemblyHandlers.onLocationTrackingEvent(new LocationRemoved(connection));
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse, new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state")));
    }

    /**
     * Validation Accepted(Move Command) - Given assembly is in ready state,
     * when move command is submitted
     * then validation should be successfull and accepted response should be returned.
     */
    @Test
    public void moveCommandTest(){
      //  PowerMockito.mockStatic(Optional.class);
       // when(Optional.of(any(JCommandService.class))).thenReturn(enclowCommandOpt);
       // assemblyHandlers.onLocationTrackingEvent(new LocationUpdated(location));
        assemblyHandlers.getMonitorActor().tell(new JMonitorActor.CurrentStateMessage(TestConstants.getReadyState()));
        Setup moveCommand = TestConstants.moveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
    }

    /**
     * Immediate Command(follow Command) - Given assembly is in ready state,
     * when follow command is submitted
     * then validation should be successful and completed response should be returned.
     */
    @Test
    public void followCommandTest() throws InterruptedException {
        //  PowerMockito.mockStatic(Optional.class);
        // when(Optional.of(any(JCommandService.class))).thenReturn(enclowCommandOpt);
        // assemblyHandlers.onLocationTrackingEvent(new LocationUpdated(location));
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        when(hcdService.submitAndSubscribe(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(followCommand.runId())));
        //assemblyHandlers.getMonitorActor().tell(new JMonitorActor.LocationEventMessage(Optional.of(hcdService)));
        assemblyHandlers.getCommandHandlerActor().tell(new JCommandHandlerActor.UpdateTemplateHcdMessage(Optional.of(hcdService)));
        assemblyHandlers.getMonitorActor().tell(new JMonitorActor.CurrentStateMessage(TestConstants.getReadyState()));
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        CommandResponse commandResponse=assemblyHandlers.validateCommand(followCommand);
        assertEquals(commandResponse, new CommandResponse.Completed(followCommand.runId()));
    }


}