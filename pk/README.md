# Pointing Kernel Based on CSW 0.4

This project implements Pointing Kernel Assembly using TMT Common Software ([CSW](https://github.com/tmtsoftware/csw-prod)) APIs. 

## Subprojects

* pk-assembly - an assembly that talks to the tpk core libraries for mount, enclosure, m3 etc. demand generation
* pk-deploy - for starting/deploying Pointing Kernel Assembly

## Examples in the project

This project shows working examples of:
1. Create typed actors for each of the internal component of Pointing Kernel: Lifecycle Actor, Command Handler Actor, Event Handler Actor
2. Set Target command flow
3. JNI Wrapper with tpk library
4. Demands getting through callback and ready for publishing

## To Be Done
1. Demands to be published out of Pointing Kernel using CSW Events Service (This will be done once CSW Event Service is available)

##  Documentation

### Creating Typed Actors
The pk code contains Typed Actors for the following assembly subcomponents:

Lifecycle Actor, Command Handler Actor and EventPublisher Actor.  
It also contains actor for command: SetTarget

#### Lifecycle Actor

The lifecycle actor contains all lifecycle related functions: functions that are performed at startup and shutdown.  

#### Command Handler Actor

The Command Handler Actor directs submit commands to appropriate workers.  Handles onGoOnline and onGoOffline actions (for now, going offline means ignoring incoming commands)

#### SetTargetCmdActor

This command demonstrates how target is set to tpk context.  This example command emulates the TPK Set Target command.

Parameter Types:

ra: double

dec: double

#### Event Handler Actor

This is not implemented fully right now. It will be taken up once CSW Event Service is available.  For now, events to be published are written to a log file.


## Build and Running the Project

### Downloading the project

Clone or download tmtsoftware/tcs-vslice-0.2 to a directory of choice

### Building the Project

cd tcs-vslice-0.2/pk

sbt stage publishLocal

### Deploying/Running the PK Assembly

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

### Start the pk-assembly Assembly

cd pk-deploy/target/universal/stage/bin

./pk-container-cmd-app --local ../../../../src/main/resources/PkContainer.conf

### Build/Run the Client App

The client app is not part of the CSW template.  It was added with the following steps:

1. Add the pk-client project directory to the pk project
2. Add pk-client to build.sbt and project/Dependencies.scala
3. Add the App object code to pk-deploy/src/main/java/org.tmt.tcs.pkdeploy as PkClientApp.scala.  This is the same location as the container starting apps.
4. Sbt build stage on the project will create the necessary scripts with jar dependencies to target/universal/stage/bin

## Build Instructions

The build is based on sbt and depends on libraries published to bintray from the 
[csw-prod](https://github.com/tmtsoftware/csw-prod) project.

See [here](https://www.scala-sbt.org/1.0/docs/Setup.html) for instructions on installing sbt.

## Pre-requisites before running Components

Make sure that the necessary environment variables are set. For example:

* Set the environment variables (Replace interface name, IP address and port with your own values):
```bash
export interfaceName=enp0s31f6
export clusterSeeds=192.168.178.77:7777
```
for bash shell, or 
```csh
setenv interfaceName enp0s31f6
setenv clusterSeeds 192.168.178.77:7777
```

for csh or tcsh. The list of available network interfaces can be found using the _ifconfig -a_ command.
If you don't specify the network interface this way, a default will be chosen, which might sometimes not be
the one you expect. 

Before running any components, follow below steps:
 - Download csw-apps zip from https://github.com/tmtsoftware/csw-prod/releases.
 - Unzip the downloaded zip
 - Go to the bin directory where you will find `csw-services.sh` script.
 - Run `./csw_services.sh --help` to see more information
 - Run `./csw_services.sh start` to start location service and config server
 
 
### Changing Demand Generation Tick Interval  
Edit file - TPC_POC/impl.cpp , In Method 'FastScan' inside Class 'FastScan' put appropriate tick interval. build project using make, this will generate libexample.so . place this file in shared lib location
