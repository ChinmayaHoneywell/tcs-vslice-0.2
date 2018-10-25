package org.tmt.encsubsystem.encassembly;

import csw.messages.commands.CommandName;
import csw.messages.commands.Setup;
import csw.messages.javadsl.JUnits;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.models.Prefix;
import csw.messages.params.states.CurrentState;
import csw.messages.params.states.StateName;
import org.tmt.encsubsystem.encassembly.model.HCDState;

import java.util.Optional;

public class TestConstants {
    /**
     * Ref -
     * How to test if actor has executed some function?
     * https://stackoverflow.com/questions/27091629/akka-test-that-function-from-test-executed?answertab=oldest#tab-top
     */
    public static final int ACTOR_MESSAGE_PROCESSING_DELAY = 10000;



    public static Setup moveCommand(){
        Long[] timeDurationValue = new Long[1];
        timeDurationValue[0] = 10L;
        Setup moveCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyTypes.StringKey().make("operation").set("On"))
                .add(JKeyTypes.DoubleKey().make("az").set(2.34))
                .add(JKeyTypes.DoubleKey().make("el").set(5.76))
                .add(JKeyTypes.StringKey().make("mode").set("fast"))
                .add(JKeyTypes.LongKey().make("timeDuration").set(timeDurationValue, JUnits.second));

        return moveCommand;
    }

    public static Setup invalidMoveCommand(){
        Long[] timeDurationValue = new Long[1];
        timeDurationValue[0] = 10L;
        Setup moveCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyTypes.StringKey().make("operation").set("On"))
                .add(JKeyTypes.DoubleKey().make("az").set(2.34))
                .add(JKeyTypes.DoubleKey().make("el").set(5.76))
                .add(JKeyTypes.LongKey().make("timeDuration").set(timeDurationValue, JUnits.second));

        return moveCommand;
    }

    public static CurrentState getReadyState(){
        CurrentState state = new CurrentState("tmt.tcs.ecs", new StateName("HcdState"))
                .add(JKeyTypes.StringKey().make("LifecycleState").set(HCDState.LifecycleState.Running.name()))
                .add(JKeyTypes.StringKey().make("OperationalState").set(HCDState.OperationalState.Ready.name()));
        return state;

    }
}
