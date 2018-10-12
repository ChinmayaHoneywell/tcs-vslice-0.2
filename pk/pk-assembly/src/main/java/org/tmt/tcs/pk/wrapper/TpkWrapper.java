package org.tmt.tcs.pk.wrapper;

import akka.actor.ActorRef;
import org.tmt.tcs.pk.pkassembly.JPkEventHandlerActor;

import java.util.Optional;

/**
 * This is a wrapper class for TPK and will act as an endpoint. It helps in
 * calling TPK New Target and Offset methods so that specific demands can be
 * generated by TPK System
 *
 */
public class TpkWrapper {

    private TpkPoc tpkEndpoint;

    private boolean publishDemands = false;
    private akka.actor.typed.ActorRef<JPkEventHandlerActor.EventMessage> eventHandlerActor;

    public TpkWrapper(akka.actor.typed.ActorRef<JPkEventHandlerActor.EventMessage> eventHandlerActor){
        this.eventHandlerActor = eventHandlerActor;
    }

    /**
     * Callback which is register with the C++ code and call from the fast
     * loop
     *
     */
    public class DemandsCallback extends IDemandsCB {

        double ci = 32.5;
        double ciz = 90 - ci;
        double phir = Math.PI * ci / 180;
        double tci = Math.tan(ci);
        double cci = Math.cos(ci);
        double PI2 = Math.PI * 2;

        public void newDemands(double mcsAz, double mcsEl, double ecsAz, double ecsEl, double m3Rotation,
                               double m3Tilt) {

            // Convert eAz, eEl into base & cap coordinates
            double azShift, base1, cap1, base2, cap2;
            if ((ecsEl > PI2) || (ecsEl < 0))
                ecsEl = 0;
            if ((ecsAz > PI2) || (ecsAz < 0))
                ecsAz = 0;

            cap1 = Math.acos(Math.tan(ecsEl - ciz) / tci);
            cap2 = PI2 - cap1;

            if (ecsEl == PI2)
                azShift = 0;
            else
                azShift = Math.atan(Math.sin(cap1) / cci * (1 - Math.cos(cap1)));

            if ((ecsAz + azShift) > PI2)
                base1 = (ecsAz + azShift) - PI2;
            else
                base1 = ecsAz + azShift;

            if (ecsAz < azShift)
                base2 = PI2 + ecsAz - azShift;
            else
                base2 = ecsAz - azShift;

            base1 = 180 * base1 / Math.PI;
            cap1 = 180 * cap1 / Math.PI;

            // Below condition will help in preventing TPK Default Demands
            // from getting published and Demand Publishing will start only
            // once New target or Offset Command is being received
            if (publishDemands) {
                // System.out.printf("%.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n",
                // mAz, mEl, base1, cap1, m3R, m3T);

                //Calling publishEcsPosDemand & publishMcsPosDemand to be done here
                System.out.println("Inside SetTargetCmdActor TpkWrapper: newDemands callback: mcsAz is: " + mcsAz + ": mcsEl is: " + mcsEl);
            }
        }
    }

    /**
     * This will help registering and Initializing TPK, once this method is
     * invoked TPK will start generation default Demands
     */
    public void initiate() {
        TpkWrapper.DemandsCallback cb = new TpkWrapper.DemandsCallback();
        tpkEndpoint = new TpkPoc();
        tpkEndpoint._register(cb);

        tpkEndpoint.init();
    }

    /**
     * New target from Ra, Dec in degrees. Target applies to Mount and
     * Enclosure
     *
     * @param ra
     * @param dec
     */
    public void newTarget(double ra, double dec) {
        publishDemands = true;
        System.out.println("Inside SetTargetCmdActor TpkWrapper: newTarget");
        tpkEndpoint.newTarget(ra, dec);
    }

    /**
     * This helps in publishing MCS specific Az and El being generated by TPK as
     * Demand
     *
     * @param eventPublisher
     */
    public void publishMcsPosDemand(Optional<ActorRef> eventPublisher) {
        System.out.println("Inside SetTargetCmdActor: publishMcsPosDemand publish demand");
    }

    /**
     * This helps in publishing ECS specific Az and El being generated by TPK as
     * Demand
     *
     * @param eventPublisher
     */
    public void publishEcsPosDemand(Optional<ActorRef> eventPublisher) {
        System.out.println("Inside SetTargetCmdActor: publishEcsPosDemand publish demand");
    }

}
