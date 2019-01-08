# TCS MCS POC (Scala, CSW 0.6.0)

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
`./csw-location-server --clusterPort=5552`  

Once location server is started, In a new terminal initialize configuration repo using  
`./csw-config-server --initRepo`

If init repo does not work try deleting 'csw-config-svn' folder  
`cd /tmp`  
`rm -rf csw-config-svn`  

Now again try to initialize config repo.

Once config server is initialized properly, later all csw services can be started or stopped using  
`./csw-services.sh start`  
`./csw-services.sh stop`  

#### CSW Logs  
To varify if csw services are working properly, csw logs can be check at  
`cd /tmp/csw/logs`  

## Build and Running the MCS POC

### Downloading the template

Clone or download tmtsoftware/tcs-vslice-0.2/MCS to a directory of choice

### Building the template

`cd tcs-vslice-0.2/mcs`  
`sbt stage publishLocal`  

### Populate configurations for Assembly and HCD
These Below steps needs to be done everytime cofig service is re-initialized due to any issue because initializing config server deletes all the config data.

#### Create Assembly configuration
`curl -X POST --data 'tmt{tcs{mcs{cmdtimeout:10,retries:2,limit:1}}}' http://<ip address of config service>:<config service port number>/config/org/tmt/tcs/mcs_assembly.conf`  
e.g.  
`curl -X POST --data 'tmt{tcs{mcs{cmdtimeout:10,retries:2,limit:1}}}' http://192.168.2.8:4000/config/org/tmt/tcs/mcs_assembly.conf`

#### Create HCD configuration
`curl -X POST --data 'tmt{tcs{mcs{TCSMCSAddr:"tcp://<ip address of TCS server>:",MCSSimulatorAddr:"tcp://<ip address of MCS real simulator>:",zeroMQPush:55579,zeroMQPull:55578,zeroMQPub:55581,zeroMQSub:55580}}}' http://<ip address of config service>:<config service port>/config/org/tmt/tcs/mcs_hcd.conf`

e.g.
`curl -X POST --data 'tmt{tcs{mcs{TCSMCSAddr:"tcp://192.168.2.7:",MCSSimulatorAddr:"tcp://192.168.2.8:",zeroMQPush:55579,zeroMQPull:55578,zeroMQPub:55581,zeroMQSub:55580}}}' http://192.168.2.8:4000/config/org/tmt/tcs/mcs_hcd.conf`


TCSMCSAddr corresponds to ipaddress of machine where MCS is running and MCSSimulatorAddr is ipaddress of machine where MCS Real simulator is running. if both are running on same machine then both will have same ip address.


#### Cross check whether Assembly config file added successfully or not
'curl -X GET http://<ip address of config service>:<config service port number>/config/org/tmt/tcs/mcs_assembly.conf` 
e.g. 'curl -X GET http://192.168.2.8:4000/config/org/tmt/tcs/mcs_assembly.conf` 

#### Cross check whether HCD config file added successfully or not
`curl -X GET http://192.168.2.8:4000/config/org/tmt/tcs/mcs_hcd.conf` 

#### Log Files path setup:
Export below environment variable. This is where events/commands measurement data csv file will be generated
export LogFiles=<Path of the folder in which log files should be generated>
e.g.: export LogFiles=/home/tmt_tcs_2/LogFiles/scenario5


### Start the MCS Assembly

`cd mcs-deploy/target/universal/stage/bin`  
`./mcs-container-cmd-app --local ../../../../src/main/resources/McsContainer.conf`

### Run the Client App

`cd mcs-deploy/target/universal/stage/bin`  
`./mcs-main-app`

Client app sends setSimulationMode,startup,Datum,Follow,ReadConfiguration commands to MCS Assembly.


### Run Junit Tests
sbt test

## Performance Measurement  

### Scenario III  
Single-Machine, Multiple Container with Simple Simulator.  

#### Step 1 - Start CSW services  
`./csw-services.sh start`  

#### JAVA 9  
As Java 1.8 does not support time capturing in microsecond, before starting any assembly PK or MCS, switch to JRE 9 by modifying PATH variable. This is required only for deployment and build should be done with java 8.  
`export PATH=/java-9-home-path-here/bin:$PATH`  

#### Step 2 - Start Pointing Kernel Assembly  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-container-cmd-app --local ../../../../src/main/resources/PkContainer.conf`  

#### Step 3 - Start MCS Assembly  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-container-cmd-app --local ../../../../src/main/resources/McsContainer.conf`  

#### Step 4 - Start Jconsole and connect to MCS Container process from it.
`jconsole`  

#### Step 5 - Start MCS Real Simulator 
No need to start real simulator for this scenario

#### Step 6 - Start Event generation in MCS  
By default the mode is set to simple simulator. Varify and if required Edit and rebuild mcs-main-app before executing below commands to use simple simulator mode. 

`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-main-app`  

#### Step 7 - Start Demand generation in PK  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-client-app`  


In around 15-20 min you will see measurment data is generated at location specified using environment variable 'LogFiles'. This will be in csv format.
Save the jconsole data as well.
Stop all the services and redo above steps to take another set of measurements.


### Scenario IV  
Single-Machine, Multiple Container with Real Simulator.  

#### Step 1 - Start CSW services  
`./csw-services.sh start`  

#### JAVA 9  
As Java 1.8 does not support time capturing in microsecond, before starting any assembly PK or MCS, switch to JRE 9 by modifying PATH variable. This is required only for deployment and build should be done with java 8.  
`export PATH=/java-9-home-path-here/bin:$PATH`  

#### Step 2 - Start Pointing Kernel Assembly  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-container-cmd-app --local ../../../../src/main/resources/PkContainer.conf`  

#### Step 3 - Start MCS Assembly  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-container-cmd-app --local ../../../../src/main/resources/McsContainer.conf`  

#### Step 4 - Start Jconsole and connect to MCS Container process from it.
`jconsole`  

#### Step 5 - Start MCS Real Simulator 
`cd tcs-vsclice-0.2/MCSSubsystem/`  
`sbt compile package`  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`sbt run`  

#### Step 6 - Start Event generation in MCS  
By default the mode is set to simple simulator. Varify and if required Edit and rebuild mcs-main-app before executing below commands to use real simulator mode. 

`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-main-app`  

#### Step 7 - Start Demand generation in PK  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-client-app`  

In around 15-20 min you will see measurment data is generated at location specified using environment variable 'LogFiles'. This will be in csv format.
Save the jconsole data as well.
Stop all the services and redo above steps to take another set of measurements.


### Scenario V  
Multi-Machine, Multiple Container with Real Simulator.  


#### CSW Multimachine Setup  
Assuming two machines with Machine-1-IP: 192.168.2.8 and Machine-1-IP: 192.168.2.7, Setup below environment variable

##### Machine-1 Setup  
export interfaceName=eno1  
export clusterSeeds=192.168.2.8:5552,192.168.2.7:5552  

Now run csw services on machien-1.  
`./csw-services.sh start`  

##### Machine-2 Setup  
export interfaceName=eno1  
export clusterSeeds=192.168.2.8:5552,192.168.2.7:5552  

Now run only location server on machine-2  
`./csw-location-server --clusterPort=5552`  

#### Updating MCS Assembly and Real Simulator IP Configuration 
We are going to run MCS Assembly on machine-2 and run PK assembly+Real simulator on machine-1. Machine-1 is also where all CSW services are running including config service. 

 - Modify MCS-HCD configuration to have correct RealSimulator IP by using curl post command as desribed in this doc in starting.
 - Modify and rebuild RealSimulator to have correct MCS-Assembly Machine-2 IP 

#### JAVA 9  
As Java 1.8 does not support time capturing in microsecond, before starting any assembly PK or MCS, switch to JRE 9 by modifying PATH variable. This is required only for deployment and build should be done with java 8.  
`export PATH=/java-9-home-path-here/bin:$PATH`  

#### Step 2 - Start Pointing Kernel Assembly on Machine-1  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-container-cmd-app --local ../../../../src/main/resources/PkContainer.conf`  

#### Step 3 - Start MCS Assembly on Machine-2  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-container-cmd-app --local ../../../../src/main/resources/McsContainer.conf`  

#### Step 4 - Start Jconsole and connect to MCS Container process from it on Machine-2.  
`jconsole`  

#### Step 4 - Start wireshark on Machine-1
select root user using su command
`wireshark`  
In wireshark GUI, start packate capturing by selecting interface on which csw event service is running.

#### Step 5 - Start MCS Real Simulator on Machine-1
`cd tcs-vsclice-0.2/MCSSubsystem/`  
`sbt compile package`  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`sbt run`  

#### Step 6 - Start Event generation in MCS on Machine-2  
By default the mode is set to simple simulator. Varify and if required Edit and rebuild mcs-main-app before executing below commands to use real simulator mode. Run mcs-main-app from Machine-2

`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.2/mcs/mcs-deploy/target/universal/stage/bin`  
`./mcs-main-app`  

#### Step 7 - Start Demand generation in PK  on Machine-1  
`cd tcs-vsclice-0.2/pk/pk-deploy/target/universal/stage/bin`  
`./pk-client-app`  

In around 15-20 min you will see measurment data is generated at location specified using environment variable 'LogFiles'. This will be in csv format.

Position demand date will be collected on Machine-1 by RealSimulator at 'LogFiles' location
Current Position data will be collected on Machine-2 by mcs-main-app at 'LogFiles' location

Save the jconsole data and wireshark data and tale screen shots.
Stop all the services and redo above steps to take another set of measurements.
