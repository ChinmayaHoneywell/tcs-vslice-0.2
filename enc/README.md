# TCS ENC Assembly POC (Java, CSW 0.4.0)

This project implements a TCS-ENC Assembly and ENC HCD using TMT Common Software

([CSW](https://github.com/tmtsoftware/csw-prod)) APIs. 



## Subprojects
* enc-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* enc-hcd - an HCD that the assembly communicates with

* enc-deploy - for starting/deploying the Assembly and HCD, Client to submit commands to enc-assembly.


## Examples in the ENC POC

This template shows working examples of:

1. Create typed actors for each of the internal components in the TCS architecture doc:
	Lifecycle Actor, Monitor Actor, Command Handler Actor, Event Handler Actor, State Publisher Actor

2. Move Command with parameters

	2.1 Parameter based validation inside 'onValidate'

	2.2 Subscribe to response for long running command

	2.3 FastMove command as child command to HCD

	2.4 Failing validation for invalid parameter

	2.5 State based validation using ask pattern, Accept command only if Operational state is ready.

	2.6 State transition to InPosition

3. Follow Command with parameters as Immediate command

	3.1 follow command to assembly then hcd

	3.2 Using ask pattern to implement immediate command

	3.3 State transition to Slewing

4. HCD to Assembly CurrentState publish/subscribe using Current State publisher

5. Lifecycle Commands(StartUp/Shutdown)

	5.1 Submit lifecycle command to assembly and hcd

	5.2 Transition assembly and hcd state from initialized to running and vice-versa

6. Loading and using configuration with the configuration service

7. Lifecycle and Operational states

	7.1 Transition between states

	7.2 Communicating states between actors

	7.3 Communicating states from hcd to assembly

8. Client app to submit commands to assembly

	8.1 Submit command by typing on console.

9. Junit Test Cases

	9.1 Follow Command Actor Test case for command completion.
	9.2 FastMove Command Actor Test case for  command completion.

10. Other Enhancements

	10.1 Updated code to work with latest csw version (29th June)
	10.2 Current state name
	10.3 Configuration usage(TBD)
	10.4 Simulator -Real vs Simple(TBD)

## Examples to be implemented

1. Junit test cases.


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

#### Event Handler Actor
Event Handler Actor will receive Current States changes from Monitor actor, then convert them to events to publish using CSW Event Service.

#### State Publisher Actor
State Publisher Actor in HCD publishes Current State to Assembly by using Current State Publisher of CSW.
Current State can be current position of enclosure base and cap, it can be lifecycle state or operational state.

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

#### FastMoveCmdActor
HCD Actor to handle incoming command

Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None).add(operation).add(az).add(el).add(mode)

Parameter Types:

 operation : string
 az: double
 el: double
 mode: string

#### FollowCmdActor
This command demonstrates immediate command implementation. In this command completion or error status is return from 'OnValidate' hook and 'OnSubmit' hook will not be called.

Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None)

#### TrackOffCmdActor
HCD Actor to handle incoming command
Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None)


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

cd sw-prod

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

The Client App accept user input on console. Following command can be submitted to assembly by typing their name on console.
[startup, invalidMove, move, follow, shutdown]

Or user can type 'exit' to stop client.

### Run Junit Tests
sbt test
