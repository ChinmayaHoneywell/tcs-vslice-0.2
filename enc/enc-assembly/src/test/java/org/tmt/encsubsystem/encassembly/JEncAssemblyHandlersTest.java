package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandResponse;
import csw.messages.framework.ComponentInfo;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.event.javadsl.IEventService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.JLoggerFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * Tests in this class are Asynchronous. All Actor are created using akka test kit.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(JConfigClientFactory.class)
public class JEncAssemblyHandlersTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

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
    ILocationService locationService;

    @Mock
    IEventService eventService;

    @Mock
    IConfigClientService configClientApi;

    JLoggerFactory jLoggerFactory;

    JComponentHandlers assemblyHandlers;


    @Before
    public void setUp() {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        PowerMockito.mockStatic(JConfigClientFactory.class);
        when(JConfigClientFactory.clientApi(any(), any())).thenReturn(configClientApi);
        when(ctx.getSystem()).thenReturn(testKit.system());
        when(ctx.spawnAnonymous(any(Behavior.class))).thenAnswer(i->{
            return testKit.spawn(i.getArgument(0));
        });
        JEncAssemblyBehaviorFactory factory = new JEncAssemblyBehaviorFactory();
        assemblyHandlers = factory.jHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, jLoggerFactory);
    }

    @After
    public void tearDown() {
    }

    /**
     * Given assembly is in ready state,
     * when HCD connection is lost and move command is submitted
     * then command should fail and invalid state command response should be returned.
     */
    @Test
    public void stateValidationTest(){
        CommandResponse commandResponse=assemblyHandlers.validateCommand(TestConstants.moveCommand());
        System.out.println(commandResponse.resultType());
    }
}