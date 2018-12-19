# TCS MCS Assembly POC (Java, CSW 0.6.0)

This project implements a TCS-MCS Assembly and MCS HCD using TMT Common Software

## Subprojects

* MCS-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* MCS-hcd - an HCD that the assembly communicates with

* MCS-deploy - for starting/deploying the Assembly and HCD, Client to submit commands to MCS-assembly and log events.


### CSW Setup

#### Set up appropriate environment variables

Add the following lines to ~/.bashrc (on linux, or startup file appropriate to your linux shell):

`export interfaceName=machine interface name;`  
`export clusterSeeds=IP:5552`

The IP and interface name of your machine can be obtained by running: ifconfig

#### Download and Run CSW

Download CSW-APP to a directory of choice and extract  
https://github.com/tmtsoftware/csw/releases

Download and unzip csw app.  
For the first time start location service and configuration service using initRepo argument.
`cd csw-apps-0.6.0/bin`  
`./csw-cluster-seed --clusterPort 5552`  
`./csw-config-server --initRepo`

Then later all csw services can be started or stopped using  
`./csw-services.sh start`  
`./csw-services.sh stop`  

## Build and Running the Template

### Downloading the template

Clone or download tmtsoftware/tcs-vslice-0.2/MCS to a directory of choice

### Building the template

`cd tcs-vslice-0.2/MCS`  
`sbt stage publishLocal`  

### Populate configurations for Assembly and HCD

#### Create Assembly configuration

`cd MCS-deploy/src/main/resources/`  
`curl -X POST --data 'tmt{tcs{mcs{cmdtimeout:10,retries:2,limit:1}}}' http://192.168.122.1:4000/config/org/tmt/tcs/mcs_assembly.conf`

#### Create HCD configuration

`cd MCS-hcd/src/main/resources/`  
`curl -X POST --data 'tmt{tcs{mcs{zeroMQPush:55579,zeroMQPull:55578,zeroMQPub:55581,zeroMQSub:55580}}}' http://192.168.122.1:4000/config/org/tmt/tcs/mcs_hcd.conf`  

### Start the MCS Assembly

`cd MCS-deploy/target/universal/stage/bin`  
`./MCS-container-cmd-app --local ../../../../src/main/resources/MCSContainer.conf`

### Run the Client App

`cd MCS-deploy/target/universal/stage/bin`  
`./mcs-main-app`



### Run Junit Tests
sbt test

## Examples in the MCS POC

This template shows working examples of:

1. Create typed actors for each of the internal components in the TCS architecture doc:
	Lifecycle Actor, Monitor Actor, Command Handler Actor, Event Handler Actor, State Publisher Actor

2. Move Command with parameters

	2.1 Parameter based validation inside 'onValidate'

	2.2 Subscribe to response for long running command

	2.3 Point and PointDemand command as child command to HCD

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

##  Documentation

### Creating Typed Actors
The template code creates Typed Actors for the following assembly subcomponents:
Lifecycle Actor, Monitor Actor, Command Handler Actor and EventPublisher Actor.  
Also actors for each command:  Move, Track, FastMove, TrackOff

#### Lifecycle Actor
The lifecycle actor contains all lifecycle related functions: functions that are performed at startup and shutdown.  Loading configuration and connecting to HCDs and other Assemblies as needed.

#### Monitor Actor
Health monitoring for the assembly.  Tracks dependMCSy location changes and monitors health and state of the assembly.

#### Command Handler Actor
Directs submit commands to appropriate workers.  Handles onGoOnline and onGoOffline actions (for now, going offline means ignoring incoming commands)

#### Event Handler Actor
Event Handler Actor will receive Current States changes from Monitor actor, then convert them to events to publish using CSW Event Service.

#### State Publisher Actor
State Publisher Actor in HCD publishes Current State to Assembly by using Current State Publisher of CSW.
Current State is events published by MCS simulator and current state of HCD.

