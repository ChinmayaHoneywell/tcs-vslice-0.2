package org.tmt.encsubsystem.encassembly;

import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;

import java.time.Instant;

public class Constants {
    /**
     * event and current state alias for current position
     */
    public static final  String CURRENT_POSITION = "currentPosition";
    //event and current state alias for health
    public static final  String HEALTH = "health";
    /**
     * event and current state alias for HCD lifecycle and operational state
     */
    public static final  String HCD_STATE = "OpsAndLifecycleState";

    //event alias for assembly state
    public static final String ASSEMBLY_STATE = "assemblyState";


    //Keys to represent lifecycle state and operational state, parameters will be created from these keys
    public static final Key<String> LIFECYCLE_KEY = JKeyTypes.StringKey().make("LifecycleState");
    public static final Key<String> OPERATIONAL_KEY = JKeyTypes.StringKey().make("OperationalState");
    // The time at which asssembly state was derived by monitor actor.
    public static final Key<Instant> TIME_OF_STATE_DERIVATION = JKeyTypes.TimestampKey().make("TimeOfStateDerivation");

    public  static final int ASSEMBLY_STATE_EVENT_FREQUENCY_IN_HERTZ = 20;


    //current position keys
    public static final Key<Double> BASE_POS_KEY = JKeyTypes.DoubleKey().make("basePosKey");
    public static final Key<Double> CAP_POS_KEY = JKeyTypes.DoubleKey().make("capPosKey");

    //health keys
    //keys for health
    public static final Key<String> HEALTH_KEY = JKeyTypes.StringKey().make("healthKey");
    public static final Key<String> HEALTH_REASON_KEY = JKeyTypes.StringKey().make("healthReasonKey");
    public static final Key<Instant> HEALTH_TIME_KEY = JKeyTypes.TimestampKey().make("healthTimeKey");


    //this is the time when subsystem published current position.
    public static final Key<Instant> SUBSYSTEM_TIMESTAMP_KEY = JKeyTypes.TimestampKey().make("subsystemTimestampKey");
    //this is the time when ENC HCD processed current position.
    public static final Key<Instant> HCD_TIMESTAMP_KEY = JKeyTypes.TimestampKey().make("hcdTimestampKey");
    //this is the time when Assembly processed current position.
    public static final Key<Instant> ASSEMBLY_TIMESTAMP_KEY = JKeyTypes.TimestampKey().make("assemblyTimestampKey");

}
