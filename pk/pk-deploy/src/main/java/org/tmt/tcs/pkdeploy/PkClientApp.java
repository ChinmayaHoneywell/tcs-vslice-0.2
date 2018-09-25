package org.tmt.tcs.pkdeploy;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import csw.messages.commands.CommandResponse;
import csw.messages.params.models.ObsId;
import csw.messages.params.models.Prefix;
import csw.services.location.commons.ClusterAwareSettings;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JLocationServiceFactory;

import csw.services.logging.javadsl.JLoggingSystemFactory;
import scala.concurrent.Await;
import org.tmt.tcs.client.*;
import scala.concurrent.duration.FiniteDuration;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PkClientApp {

    public static void main(String[] args) throws Exception {

        ActorSystem system = ClusterAwareSettings.system();
        ILocationService locationService = JLocationServiceFactory.make();

        PkClient pkClient   = new PkClient(new Prefix("tcs.pk"), system, locationService);
        Optional<ObsId> maybeObsId          = Optional.empty();
        String hostName                = InetAddress.getLocalHost().getHostName();
        JLoggingSystemFactory.start("PkClientApp", "0.1", hostName, system);


        CompletableFuture<CommandResponse> cf1 = pkClient.setTarget(maybeObsId, 185.79, 6.753333);
        CommandResponse resp1 = cf1.get();
        System.out.println("Inside PkClientApp: setTarget response is: " + resp1);

    }
}
