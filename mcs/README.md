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

####CSW Logs  
To varify if csw services are working properly, csw logs can be check at  
`cd /tmp/csw`  

## Build and Running the Template

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
`curl -X POST --data 'tmt{tcs{mcs{cmdtimeout:10,retries:2,limit:1}}}' http://192.168.122.1:4000/config/org/tmt/tcs/mcs_assembly.conf`

#### Create HCD configuration
`curl -X POST --data 'tmt{tcs{mcs{TCSMCSAddr:"tcp://192.168.122.1:",MCSSimulatorAddr:"tcp://192.168.122.1:",zeroMQPush:55579,zeroMQPull:55578,zeroMQPub:55581,zeroMQSub:55580}}}' http://<ip address of config service>:<config service port>/config/org/tmt/tcs/mcs_hcd.conf`

e.g.
`curl -X POST --data 'tmt{tcs{mcs{TCSMCSAddr:"tcp://192.168.122.1:",MCSSimulatorAddr:"tcp://192.168.122.1:",zeroMQPush:55579,zeroMQPull:55578,zeroMQPub:55581,zeroMQSub:55580}}}' http://192.168.122.1:4000/config/org/tmt/tcs/mcs_hcd.conf`
TCSMCSAddr corresponds to ipaddress of machine where MCS is running and MCSSimulatorAddr is ipaddress of machine where MCS Real simulator is running. if both are running on same machine then both will have same ip address.


#### Cross check whether Assembly config file added successfully or not
'curl -X GET http://<ip address of config service>:<config service port number>/config/org/tmt/tcs/mcs_assembly.conf` 
e.g. 'curl -X GET http://192.168.2.8:5000/config/org/tmt/tcs/mcs_assembly.conf` 

#### Cross check whether HCD config file added successfully or not
'curl -X GET http://192.168.2.8:5000/config/org/tmt/tcs/mcs_hcd.conf` 

#### Log Files path setup:
for setting up log files path below variable should be setup in .bashrc file
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


