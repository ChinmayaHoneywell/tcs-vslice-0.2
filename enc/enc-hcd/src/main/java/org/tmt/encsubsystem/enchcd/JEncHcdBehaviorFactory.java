package org.tmt.encsubsystem.enchcd;

import akka.actor.typed.javadsl.ActorContext;
import csw.framework.CurrentStatePublisher;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.messages.TopLevelActorMessage;
import csw.messages.framework.ComponentInfo;
import csw.services.command.CommandResponseManager;
import csw.services.event.api.javadsl.IEventService;
import csw.services.alarm.api.javadsl.IAlarmService;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.JLoggerFactory;

public class JEncHcdBehaviorFactory extends JComponentBehaviorFactory {


    @Override
    public JComponentHandlers jHandlers(ActorContext<TopLevelActorMessage> ctx,
                                        ComponentInfo componentInfo,
                                        CommandResponseManager commandResponseManager,
                                        CurrentStatePublisher currentStatePublisher,
                                        ILocationService locationService,
                                        IEventService eventService,
                                        IAlarmService alarmService,
                                        JLoggerFactory loggerFactory) {
        return new JEncHcdHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, eventService, alarmService, loggerFactory);
    }

}
