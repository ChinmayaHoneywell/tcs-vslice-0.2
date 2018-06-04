# TCS ENC Assembly POC (Java, CSW 0.4.0)



This project implements a TCS-ENC Assembly using TMT Common Software 

([CSW](https://github.com/tmtsoftware/csw-prod)) APIs. 



## Subprojects



* enc-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* enc-hcd - an HCD that the assembly communicates with

* enc-deploy - for starting/deploying the Assembly and HCD, Client to submit commands to enc-assembly.







## Examples in the ENC POC



This template shows working examples of:

1. Create typed actors for each of the internal components in the TCS architecture doc: Lifecycle Actor, MonitorActor, Command Handler Actor, Event Handler Actor

2. Move Command with parameters, Long running command.

	2.1 Parameter based validation inside 'onValidate'

	2.2 Subscribe to response for long running command

	2.3 FastMove command as child command to HCD

	2.4 Failing validation for invalid parameter

3. Follow Command with parameters, Long running command.

	3.1  TrackOff command as child command to HCD

4. HCD to Assembly CurrentState publish/subscribe

5. Client app to exercise the assembly commands



## Examples to be implemented

1. Immediate command

2. Loading and using configuration with the configuration service

3. Lifecycle Commands(StartUp/Shutdown)

4. Communicating states between actors

5. State based command validation

6. Publish demandState from assembly to hcd

7. States transition.



##  Documentation



### Creating Typed Actors



The template code creates Typed Actors for the following assembly subcomponents:



Lifecycle Actor, Monitor Actor, Command Handler Actor and EventPublisher Actor.  

Also actors for each command:  Move, Track, FastMove, TrackOff



#### Lifecycle Actor



The lifecycle actor contains all lifecycle related functions: functions that are performed at startup and shutdown.  Loading configuration and connecting to HCDs and other Assemblies as needed.



#### Monitor Actor



Health monitoring for the assembly.  Tracks dependency location changes and monitors health and state of the assembly.



#### Command Handler Actor



Directs submit commands to appropriate workers.  Handles onGoOnline and onGoOffline actions (for now, going offline means ignoring incoming commands)





#### MoveCmdActor



This command demonstrates how command aggregation, validation and long running command can be implemented. 

FastMove command is submit to HCD.



Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;move&quot;), None).add(operation).add(az).add(el).add(mode).add(timeduration)



Parameter Types:

 operation : string

 

 az: double



 el: double

 

 mode: string

 

 timeduration: long

 

 

#### FollowCmdActor



This command demonstrates how command aggregation, validation and long running command can be implemented.

TrackOff Command is submitted to HCD.



Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None).add(operation).add(az).add(el).add(mode).add(timeduration)



Parameter Types:

 operation : string

 

 az: double



 el: double

 

 mode: string

 

 timeduration: long

 

 

#### FastMoveCmdActor



HCD Actor to handle incoming command



Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None).add(operation).add(az).add(el).add(mode)



Parameter Types:

 operation : string

 

 az: double



 el: double

 

 mode: string

 

#### TrackOffCmdActor



HCD Actor to handle incoming command



Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None)



#### Event Handler Actor



TBD



### Using the Configuration Service



TBD



### Support for State Reporting



TBD















## Build and Running the Template



### Downloading the template



Clone or download tmtsoftware/tcs-vslice-0.2/enc to a directory of choice



### Building the template



cd tcs-vslice-0.2/enc



sbt stage publishLocal



### Deploying/Running the Template Assembly



#### Set up appropriate environment variables



Add the following lines to ~/.bashrc (on linux, or startup file appropriate to your linux shell):



export interfaceName=&lt;machine interface name&gt;   (The interface name of your machine can be obtained by running: ifconfig -a | sed &#39;s/[\t].\*//;/^$/d&#39;)



export clusterSeeds=&lt;machine IP&gt;:7777



#### Install csw-prod



Clone or download tmtsoftware/csw-prod project to a directory of choice



cd csw-prod



sbt stage publishLocal



#### Start the csw-prod Location Service:



cd csw-prod/target/universal/stage/bin



./csw-cluster-seed --clusterPort 7777



#### Start the csw-prod Configuration Service:



cd csw-prod/target/universal/stage/bin



./csw-config-server --initRepo



### Populate the assembly configuration



cd enc-deploy/src/main/resources



./initialize-config.sh <ip address>



### Start the enc Assembly



cd enc-deploy/target/universal/stage/bin



./enc-container-cmd-app --local ../../../../src/main/resources/EncContainer.conf



### Run the Client App



cd tcs-deploy/target/universal/stage/bin



./tcs-template-java-client

