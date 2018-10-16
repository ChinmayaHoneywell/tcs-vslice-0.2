package org.tmt.encsubsystem.enchcd;

public class Constants {
    /**
     * Current State name for current position.
     */
    public static final  String CURRENT_POSITION = "currentPosition";
    public static final  String HEALTH = "health";
    /**
     * current state name and Object to represent lifecycle state and operational state of hcd/subsystem
     */
    public static final  String HCD_STATE = "OpsAndLifecycleState";

    public  static final int CURRENT_POSITION_PUBLISH_FREQUENCY = 100;//Hz
    public  static final int HEALTH_PUBLISH_FREQUENCY = 50;//Hz


    public static final boolean USE_SIMPLE_SIMULATOR = true;
}
