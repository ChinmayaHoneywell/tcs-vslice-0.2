package org.tmt.enc.client;


import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.util.Timeout;
import csw.messages.commands.CommandName;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.Setup;
import csw.messages.javadsl.JUnits;
import csw.messages.location.AkkaLocation;
import csw.messages.location.ComponentId;
import csw.messages.location.Connection;
import csw.messages.params.generics.JKeyTypes;
import csw.messages.params.generics.Key;
import csw.messages.params.models.Id;
import csw.messages.params.models.ObsId;
import csw.messages.params.models.Prefix;
import csw.services.command.javadsl.JCommandService;
import csw.services.location.commons.ClusterAwareSettings;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JLocationServiceFactory;
import csw.services.logging.javadsl.JLoggingSystemFactory;
import scala.concurrent.duration.FiniteDuration;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static csw.services.location.javadsl.JComponentType.Assembly;

public class TcsTemplateJavaClient {

    Prefix source;
    ActorSystem system;
    ILocationService locationService;
    Optional<JCommandService> commandServiceOptional;

    public TcsTemplateJavaClient(Prefix source, ActorSystem system, ILocationService locationService) throws Exception {

        this.source = source;
        this.system = system;
        this.locationService = locationService;

        commandServiceOptional = getAssemblyBlocking();
    }



    private Connection.AkkaConnection assemblyConnection = new Connection.AkkaConnection(new ComponentId("EncAssembly", Assembly));


    private Key<String> targetTypeKey = JKeyTypes.StringKey().make("targetType");
    private Key<Double> wavelengthKey = JKeyTypes.DoubleKey().make("wavelength");
    private Key<String> axesKey = JKeyTypes.StringKey().make("axes");

    private Key<Double> azKey = JKeyTypes.DoubleKey().make("az");
    private Key<Double> elKey = JKeyTypes.DoubleKey().make("el");
    private Key<String> mode = JKeyTypes.StringKey().make("mode");
    //private Key<Long>  time = JKeyTypes.LongKey().make("time");
    private  Key<String> operation = JKeyTypes.StringKey().make("operation");
    private Key<Long>  timeDuration = JKeyTypes.LongKey().make("timeDuration");


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
     * Sends a datum message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse> datum(Optional<ObsId> obsId)  {

        //Optional<JCommandService> commandServiceOptional = getAssemblyBlocking();

        if (commandServiceOptional.isPresent()) {

            JCommandService commandService = commandServiceOptional.get();

            Setup setup = new Setup(source, new CommandName("datum"), obsId);


            return commandService.submitAndSubscribe(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }
    /**
     * Sends a move message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse> move(Optional<ObsId> obsId, Double az, Double el, String operationValue, String modeValue) {

        if (commandServiceOptional.isPresent()) {

            JCommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("move"), obsId)
                    .add(operation.set(operationValue))
                    .add(azKey.set(az))
                    .add(elKey.set(el))
                    .add(mode.set(modeValue))
                    .add(timeDuration.set(timeDurationValue, JUnits.second));
            System.out.println("Submitting move command to assembly...");
            return commandService.submitAndSubscribe(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }
    /**
     * Sends a invalid move message to the Assembly and returns the response
     * this move command does not have "mode" parameter.
     */
    public CompletableFuture<CommandResponse> moveInvalid(Optional<ObsId> obsId, Double az, Double el, String operationValue, String modeValue) {

        if (commandServiceOptional.isPresent()) {

            JCommandService commandService = commandServiceOptional.get();
            Long[] timeValue = new Long[1];
            timeValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("move"), obsId)
                    .add(operation.set(operationValue))
                    .add(azKey.set(az))
                    .add(elKey.set(el))
                    .add(timeDuration.set(timeValue, JUnits.second));
            System.out.println("Submitting invalid move command to assembly...");
            return commandService.submitAndSubscribe(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }

    /**
     * Sends a move message to the Assembly and returns the response
     */
    public CompletableFuture<CommandResponse> follow(Optional<ObsId> obsId, Double az, Double el, String operationValue, String modeValue) {

        if (commandServiceOptional.isPresent()) {

            JCommandService commandService = commandServiceOptional.get();
            Long[] timeDurationValue = new Long[1];
            timeDurationValue[0] = 10L;

            Setup setup = new Setup(source, new CommandName("follow"), obsId)
                    .add(operation.set(operationValue))
                    .add(azKey.set(az))
                    .add(elKey.set(el))
                    .add(mode.set(modeValue))
                    .add(timeDuration.set(timeDurationValue, JUnits.second));
            System.out.println("Submitting follow(trackOff) command to assembly...");
            return commandService.submitAndSubscribe(setup, Timeout.durationToTimeout(FiniteDuration.apply(20, TimeUnit.SECONDS)));

        } else {

            return CompletableFuture.completedFuture(new CommandResponse.Error(new Id(""), "Can't locate Assembly"));
        }


    }
	
	 public static void main(String[] args) throws Exception {
         System.out.println("TCS Client Starting..");
        ActorSystem system = ClusterAwareSettings.system();
        ILocationService locationService = JLocationServiceFactory.make();

        TcsTemplateJavaClient tcsTemplateJavaClient   = new TcsTemplateJavaClient(new Prefix("enc.enc-client"), system, locationService);
        Optional<ObsId> maybeObsId          = Optional.empty();
        String hostName                = InetAddress.getLocalHost().getHostName();
        JLoggingSystemFactory.start("TcsTemplateClientApp", "0.1", hostName, system);


         System.out.println("Commanding enclosure to move with invalid param: ");
         CompletableFuture<CommandResponse> invalidMoveCmdResponse = tcsTemplateJavaClient.moveInvalid(maybeObsId,  2.34, 5.67, "On", "fast");
         System.out.println("Response on invalid move command: " + invalidMoveCmdResponse.get());


         System.out.println("Commanding enclosure to move  fast: ");
         CompletableFuture<CommandResponse> moveCmdResponse = tcsTemplateJavaClient.move(maybeObsId, 2.34, 5.67,"On", "fast");
         CommandResponse respMoveCmd = moveCmdResponse.get();
         System.out.println("Enclosure moved: " + respMoveCmd);

         System.out.println("Commanding enclosure to TrackOff using Follow Command: ");
         CompletableFuture<CommandResponse> followCmdResponse = tcsTemplateJavaClient.follow(maybeObsId, 2.34, 5.67, "Off", "smooth");
         CommandResponse respFollowCmd = followCmdResponse.get();
         System.out.println("Enclosure Follow(trackOff): " + respFollowCmd);


     }
	
	

}






