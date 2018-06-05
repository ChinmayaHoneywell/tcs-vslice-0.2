# TCS MCS Assembly POC (Scala, CSW 0.4.0)

This project implements a TCS-MCS Assembly, HCD using TMT Common Software 
([CSW](https://github.com/tmtsoftware/csw-prod)) APIs. 



## Subprojects

* msc-assembly - a mcs assembly that implements several command types, monitors state, and loads configuration

* mcs-hcd - an HCD that the assembly communicates with

* mcs-deploy - for starting/deploying the Assembly and HCD, Client to submit commands to mcs-assembly.



## Examples in the MCS POC

This project shows working examples of:

1. Create typed actors for each of the internal components in the TCS architecture doc: Lifecycle Actor, MonitorActor, Command Handler Actor, Event Handler Actor

2. Move Command with parameters, Long running command.
	2.1 Parameter based validation inside 'onValidate'.

	2.2 Subscribe to response for long running command.

	2.3 Failing validation for invalid parameter.
	
	2.4 Move command is split into two commands at assembly level into Point  and PointDemand command.

3. Follow Command.
	3.1  transitions MCS subsystem into following state to accept position demands.
	
4. Datum Command, Long running command.

5. Lifecycle commands startup, shutdown and initialization.
	
6. HCD to Assembly CurrentState publish/subscribe.

7. Maintaing and updating States of MCS assembly and HCD.
 

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

Also actors for each command:  Move, Follow, Datum.

#### Lifecycle Actor

The lifecycle actor contains all lifecycle related functions: functions that are performed at startup and shutdown.  Loading configuration and connecting to HCDs and other Assemblies as needed.

#### Monitor Actor

Health monitoring for the assembly. Tracks hcd location changes and monitors and maintains health, state of the assembly.

#### Command Handler Actor

Directs submit commands to appropriate workers.  Handles onGoOnline and onGoOffline actions (for now, going offline means ignoring incoming commands)

#### MoveCmdActor

This command demonstrates how command aggregation, validation and long running command can be implemented. 
Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;move&quot;), None).add(operation).add(az).add(el).add(mode).add(timeduration)
Parameter Types:
 operation : string
 az: double
 el: double
 axis: string

#### FollowCmdActor

This is immediate response command. This command is not having any parameters.

### DatumCommandActor
This command performs datum operation, It takes axis to be datumed as parameter. It is long running command.
Parameter Types : 
axis : String Values :- az|el|both

#### Event Handler Actor
In assembly use of CSW event service is TBD, in case of HCD EventHandlerActor class uses currentStatePublisher to publish state events.


### Using the Configuration Service
Stores configuration of MCS assembly and HCD.





## Build and Running the Template



### Downloading the template



Clone or download tmtsoftware/tcs-vslice-0.2/mcs to a directory of choice



### Building the template



cd tcs-vslice-0.2/mcs



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



cd mcs-deploy/src/main/resources



./initialize-config.sh <ip address>



### Start the mcs Assembly



cd mcs-deploy/target/universal/stage/bin



./mcs-container-cmd-app --local ../../../../src/main/resources/McsContainer.conf


### To be done

Facing some conceptual issues related to MCS assembly state validations; so unable to compile code right now, system will compile once this is done.
