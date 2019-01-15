# MCSSubsystem
This project acts as a real simulator for actual MCS subsystem. It is developed using scala programming language. 

## Project Description
Project processes commands received over zeroMQ pull socket and sends command responses over zeroMQ push socket.
This project subscribes to events from TCS over zeroMQ sub socket and publishes events over zeroMQ pub socket.


## Project Setup

Go to src/main/resources directory and edit Simulator.conf file.
please add below configuration properties in Simulator.conf file: 

* MCSAddress - tcp address of machine on which MCS Subsystem is running.
  
  e.g.: If MCSSubsystem is running on 192.168.2.8 then value for this field can be MCSAddress="tcp://192.168.2.8:"

* TCSAddress - tcp address of machine on which TCS is running.

  e.g.: If TCS-MCS is running on 192.168.2.7 then value for this field can be TCSAddress="tcp://192.168.2.7:"

* pushSocket - socket on which MCS subsystem will push command respones.

  e.g. pushSocket=55578
  
* pullSocket - socket on which MCS subsystem will pull commands from.
 
 e.g. pullSocket=55579
 
* pubSocket - Socket on which MCS subsystem will publish events.
 
 e.g pubSocket=55580
 
* subSocket - Socket on which MCS subsystem will listen for incoming events.
 e.g subSocket=55581


## Build and Run MCSSubsystem

Please compile and build mcs subsystem on java version 1.8:

1. go to directory: MCSSubsystem and run below command:

sbt compile package run.

2. upgrade java version from 1.8 to 1.9, for upgrading use below command 

export PATH=/java-9-home-path-here/bin:$PATH

3. Run below command to start MCS subsystem:

sbt run
