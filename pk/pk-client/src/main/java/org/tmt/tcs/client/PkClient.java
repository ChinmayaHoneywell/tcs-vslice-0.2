package org.tmt.tcs.client;


import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.util.Timeout;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.Setup;
import csw.messages.location.AkkaLocation;
import csw.messages.location.ComponentId;
import csw.messages.location.Connection;
import csw.messages.params.generics.Key;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.models.Id;
import csw.messages.params.models.ObsId;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.location.javadsl.*;
import scala.concurrent.duration.FiniteDuration;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static csw.services.location.javadsl.JComponentType.Assembly;

public class PkClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;

    public PkClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {

        this.source = source;
        this.system = system;
        this.locationService = locationService;

        commandServiceOptional = getAssemblyBlocking();
    }

    Optional<JCommandService> commandServiceOptional = Optional.empty();

    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("PkAssembly", Assembly));


    private Key<Double> raKey = JKeyTypes.DoubleKey().make("ra");
    private Key<Double> decKey = JKeyTypes.DoubleKey().make("dec");


    /**
     * Gets a reference to the running assembly from the location service, if found.
     */

    private Optional<JCommandService> getAssemblyBlocking() throws Exception {

        FiniteDuration waitForResolveLimit = new FiniteDuration(30, TimeUnit.SECONDS);

        Optional<AkkaLocation> resolveResult = locationService.resolve(assemblyConnection, waitForResolveLimit).get();

        if (resolveResult.isPresent()) {

            AkkaLocation akkaLocation = resolveResult.get();

            return Optional.of(new JCommandService(akkaLocation, Adapter.toTyped(system)));

        } else {
            return Optional.empty();
        }
    }

    /**
     * Sends a setTarget message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse> setTarget(Optional<ObsId> obsId, Double ra, Double dec) throws Exception {

        if (commandServiceOptional.isPresent()) {

            JCommandService commandService = commandServiceOptional.get();

            Setup setup = new Setup(source, new CommandName("setTarget"), obsId)
                    .add(raKey.set(ra))
                    .add(decKey.set(dec));

            return commandService.submitAndSubscribe(setup, Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

   
}






