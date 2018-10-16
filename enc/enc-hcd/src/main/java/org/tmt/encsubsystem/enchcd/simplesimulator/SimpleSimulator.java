package org.tmt.encsubsystem.enchcd.simplesimulator;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * This is a simple simulator for subsystem
 *
 */
public class SimpleSimulator {

    public static final int COMMAND_PROCESSING_DELAY_MILIS= 1000;

    public static final int CURRENT_POSITION_CHANGE_DELAY= 100;

    public static final int AZ_EL_DECIMAL_PLACES= 2;
    public static final double AZ_EL_PRECISION= .01;

    private static SimpleSimulator INSTANCE;

    private CurrentPosition currentPosition;
    private Health health;

    private SimpleSimulator() {
        this.currentPosition = new CurrentPosition(0.12, 0.06, Instant.now().toEpochMilli());
        this.health = new Health(Health.HealthType.GOOD, "good", Instant.now().toEpochMilli());
    }

    public static SimpleSimulator getInstance(){
        if(INSTANCE ==null){
            INSTANCE= new SimpleSimulator();
        }
        return INSTANCE;
    }

    /**
     * this method simulates move command processing.
     * current Az El of Enclosure will slowly adjust towards submitted demand.
     * @param cmd
     * @return
     */
    public FastMoveCommand.Response sendCommand(FastMoveCommand cmd) {

        System.out.println("target position- " +cmd);
        try {
            Thread.sleep(COMMAND_PROCESSING_DELAY_MILIS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        CompletableFuture.runAsync(()->{
            double diffAz= Util.diff(cmd.getBase(), currentPosition.getBase(), AZ_EL_DECIMAL_PLACES);
            double diffEl = Util.diff(cmd.getCap(), currentPosition.getCap(), AZ_EL_DECIMAL_PLACES);
            //System.out.println("diffAz - " + diffAz + " ,  diffEl - " + diffEl);
           while(diffAz != 0 || diffEl != 0 ) {
               if(diffAz !=0) {
                   double changeAz = ((diffAz / Math.abs(diffAz)) * AZ_EL_PRECISION);
                   //System.out.println("changeAz - " + changeAz);
                   currentPosition.setBase(Util.round(currentPosition.getBase() + changeAz, AZ_EL_DECIMAL_PLACES));
               }


               if(diffEl!=0) {
                   double changeEl = ((diffEl / Math.abs(diffEl)) * AZ_EL_PRECISION);
                   //System.out.println("changeEl - " + changeEl);
                   currentPosition.setCap(Util.round(currentPosition.getCap() + changeEl, AZ_EL_DECIMAL_PLACES));
               }

                diffAz= Util.diff(cmd.getBase(), currentPosition.getBase(), AZ_EL_DECIMAL_PLACES);
                diffEl = Util.diff(cmd.getCap(), currentPosition.getCap(), AZ_EL_DECIMAL_PLACES);
               System.out.println("diffAz - " + diffAz + " ,  diffEl - " + diffEl);
              // System.out.println("current position - " + currentPosition+"  diff in Az - " + diffAz + "   diff in El - " + diffEl);
               try {
                   Thread.sleep(CURRENT_POSITION_CHANGE_DELAY);
               } catch (InterruptedException e) {
                   e.printStackTrace();
               }
           }
            System.out.println("target reached");
        });
        FastMoveCommand.Response response= new FastMoveCommand.Response();
        response.setDesc("Completed");
        response.setStatus(FastMoveCommand.Response.Status.OK);
        return response;
    }

    /**
     * This method provides current position of enc subsystem to hcd.
     * @return
     */
    public CurrentPosition getCurrentPosition() {
        currentPosition.setTime(Instant.now().toEpochMilli());
        return currentPosition;
    }

    /**
     * This method provides health of enc subsystem to hcd.
     * @return
     */
    public Health getHealth() {
        health.setTime(Instant.now().toEpochMilli());
        return health;
    }
}
