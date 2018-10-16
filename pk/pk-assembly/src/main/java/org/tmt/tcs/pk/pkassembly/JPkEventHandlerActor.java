package org.tmt.tcs.pk.pkassembly;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.MutableBehavior;
import akka.actor.typed.javadsl.ReceiveBuilder;
import csw.messages.events.Event;
import csw.messages.events.EventName;
import csw.messages.events.SystemEvent;
import csw.messages.params.models.Prefix;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;

import csw.services.event.api.javadsl.IEventService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

public class JPkEventHandlerActor extends MutableBehavior<JPkEventHandlerActor.EventMessage> {

    private ActorContext<EventMessage> actorContext;
    private IEventService eventService;
    private JLoggerFactory loggerFactory;
    private ILogger log;

    private static final Prefix prefix = new Prefix("tcs.pk");


    private JPkEventHandlerActor(ActorContext<EventMessage> actorContext, IEventService eventService, JLoggerFactory loggerFactory) {
        this.actorContext = actorContext;
        this.eventService = eventService;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(actorContext, getClass());

    }

    public static <EventMessage> Behavior<EventMessage> behavior(IEventService eventService, JLoggerFactory loggerFactory) {
        return Behaviors.setup(ctx -> {
            return (MutableBehavior<EventMessage>) new JPkEventHandlerActor((ActorContext<JPkEventHandlerActor.EventMessage>) ctx, eventService, loggerFactory);
        });
    }



    @Override
    public Behaviors.Receive<EventMessage> createReceive() {

        ReceiveBuilder<EventMessage> builder = receiveBuilder()
                .onMessage(McsDemandMessage.class,
                        message -> {
                            log.info("Inside JPkEventHandlerActor: McsDemandMessage Received");
                            publishMcsDemand(message);
                            return Behaviors.same();
                        })
                .onMessage(EncDemandMessage.class,
                        message -> {
                            log.info("Inside JPkEventHandlerActor: EncDemandMessage Received");
                            publishEncDemand(message);
                            return Behaviors.same();
                        })
                .onMessage(M3DemandMessage.class,
                        message -> {
                            log.info("Inside JPkEventHandlerActor: M3DemandMessage Received");
                            publishM3Demand(message);
                            return Behaviors.same();
                        });
        return builder.build();
    }

    private void publishMcsDemand(McsDemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing Mcs Demand ");
        Key<Double> azDoubleKey = JKeyTypes.DoubleKey().make("mcs.az");
        Key<Double> elDoubleKey = JKeyTypes.DoubleKey().make("mcs.el");

        Event event = new SystemEvent(prefix, new EventName("mcsdemandpositions")).add(azDoubleKey.set(message.getAz())).add(elDoubleKey.set(message.getEl()));
        eventService.defaultPublisher().publish(event);
    }

    private void publishEncDemand(EncDemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing Enc Demand ");
        Key<Double> baseDoubleKey = JKeyTypes.DoubleKey().make("ecs.base");
        Key<Double> capDoubleKey = JKeyTypes.DoubleKey().make("ecs.cap");

        Event event = new SystemEvent(prefix, new EventName("encdemandpositions")).add(baseDoubleKey.set(message.getBase())).add(capDoubleKey.set(message.getCap()));
        eventService.defaultPublisher().publish(event);
    }

    private void publishM3Demand(M3DemandMessage message) {

        log.info("Inside JPkEventHandlerActor: Publishing M3 Demand ");
        Key<Double> rotationDoubleKey = JKeyTypes.DoubleKey().make("m3.rotation");
        Key<Double> tiltDoubleKey = JKeyTypes.DoubleKey().make("m3.tilt");

        Event event = new SystemEvent(prefix, new EventName("m3demandpositions")).add(rotationDoubleKey.set(message.getRotation())).add(tiltDoubleKey.set(message.getTilt()));;
        eventService.defaultPublisher().publish(event);
    }

    // add messages here
    public interface EventMessage {}

    public static final class McsDemandMessage implements EventMessage {
        private final double az;
        private final double el;

        public McsDemandMessage(double az, double el){
            this.az = az;
            this.el = el;
        }

        public double getAz() {
            return az;
        }

        public double getEl() {
            return el;
        }
    }

    public static final class EncDemandMessage implements EventMessage {
        private final double base;
        private final double cap;

        public EncDemandMessage(double base, double cap){
            this.base = base;
            this.cap = cap;
        }

        public double getBase() {
            return base;
        }

        public double getCap() {
            return cap;
        }
    }

    public static final class M3DemandMessage implements EventMessage {
        private final double rotation;
        private final double tilt;

        public M3DemandMessage(double rotation, double tilt){
            this.rotation = rotation;
            this.tilt = tilt;
        }

        public double getRotation() {
            return rotation;
        }

        public double getTilt() {
            return tilt;
        }
    }

}
