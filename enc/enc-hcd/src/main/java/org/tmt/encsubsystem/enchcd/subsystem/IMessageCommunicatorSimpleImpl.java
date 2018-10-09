package org.tmt.encsubsystem.enchcd.subsystem;

import java.util.concurrent.CompletableFuture;

/**
 * This is a simple simulator for subsystem
 *
 */
public class IMessageCommunicatorSimpleImpl implements IMessageCommunicator {

    public static final int COMMAND_PROCESSING_DELAY_MILIS= 1000;

    public static final int CURRENT_POSITION_CHANGE_DELAY= 100;

    public static final int AZ_EL_DECIMAL_PLACES= 1;
    public static final int AZ_EL_PRECISION= 10;// 10^2

    private static IMessageCommunicator INSTANCE;

    private CurrentPosition currentPosition;

    private IMessageCommunicatorSimpleImpl() {
        this.currentPosition = new CurrentPosition(0.0, 0.0);
    }

    public static IMessageCommunicator getInstance(){
        if(INSTANCE ==null){
            INSTANCE= new IMessageCommunicatorSimpleImpl();
        }
        return INSTANCE;
    }

    /**
     * this method simulates move command processing.
     * current Az El of Enclosure will slowly adjust towards submitted demand.
     * @param cmd
     * @return
     */
    @Override
    public FastMoveCommand.Response sendCommand(FastMoveCommand cmd) {

        FastMoveCommand.Response response= new FastMoveCommand.Response();
        response.setDesc("Completed");
        response.setStatus(FastMoveCommand.Response.Status.OK);

        try {
            Thread.sleep(COMMAND_PROCESSING_DELAY_MILIS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        CompletableFuture.runAsync(()->{
            double diffAz= Util.diff(cmd.getAz(), currentPosition.getAz(), AZ_EL_DECIMAL_PLACES);
            double diffEl = Util.diff(cmd.getEl(), currentPosition.getEl(), AZ_EL_DECIMAL_PLACES);
           while(diffAz != 0 && diffEl != 0 ) {
               if(diffAz !=0) {
                   double changeAz = ((diffAz / Math.abs(diffAz)) / AZ_EL_PRECISION);
                   System.out.println(changeAz);
                   currentPosition.setAz(currentPosition.getAz() + changeAz);
               }


               if(diffEl!=0) {
                   double changeEl = ((diffEl / Math.abs(diffEl)) / AZ_EL_PRECISION);
                   System.out.println(changeEl);
                   currentPosition.setEl(currentPosition.getEl() + changeEl);
               }

                diffAz= Util.diff(cmd.getAz(), currentPosition.getAz(), AZ_EL_DECIMAL_PLACES);
                diffEl = Util.diff(cmd.getEl(), currentPosition.getEl(), AZ_EL_DECIMAL_PLACES);
               try {
                   Thread.sleep(CURRENT_POSITION_CHANGE_DELAY);
               } catch (InterruptedException e) {
                   e.printStackTrace();
               }
           }
        });

        return response;
    }

    /**
     * This method provides current position of enc subsystem to hcd.
     * @return
     */
    @Override
    public CurrentPosition receiveEventCurrentPosition() {
        return currentPosition;
    }
}
