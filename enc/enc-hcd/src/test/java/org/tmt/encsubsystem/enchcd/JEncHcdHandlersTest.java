package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.commands.ControlCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JEncHcdHandlersTest {

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {
    }
    //public static Behavior<String> childActor = Behaviors.receive((ctx, msg) -> Behaviors.same());

    @Test
    public void validateCommandTest() {
        JEncHcdBehaviorFactory factory = new JEncHcdBehaviorFactory();


        //TODO how to inject actor context
        //     factory.jHandlers()
    }
}