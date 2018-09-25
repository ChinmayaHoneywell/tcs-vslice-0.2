import sbt._

object Libs {
  val ScalaVersion = "2.12.6"

  val `scalatest`       = "org.scalatest"          %% "scalatest"      % "3.0.5"  //Apache License 2.0
  val `scala-async`     = "org.scala-lang.modules" %% "scala-async"    % "0.9.7"  //BSD 3-clause "New" or "Revised" License
  val `junit`           = "junit"                  % "junit"           % "4.12"   //Eclipse Public License 1.0
  val `junit-interface` = "com.novocode"           % "junit-interface" % "0.11"   //BSD 2-clause "Simplified" License
  val `mockito-core`    = "org.mockito"            % "mockito-core"    % "2.16.0" //MIT License
  val `akka-test`       = "com.typesafe.akka"      %% "akka-actor-testkit-typed"   % "2.5.13"
  //val `zeroMQ` = "org.zeromq" % "zeromq-scala-binding_2.11.0-M3" % "0.0.7"
  val `zeroMQ` = "org.zeromq" % "jeromq" % "0.4.3"
  val `protobuf`= "com.google.protobuf" % "protobuf-java" % "3.5.1"
}

object CSW {
  val Version = "0.1-SNAPSHOT"

  val `csw-location`      = "org.tmt" %% "csw-location"      % Version
  val `csw-config-client` = "org.tmt" %% "csw-config-client" % Version
  val `csw-logging`       = "org.tmt" %% "csw-logging"       % Version
  val `csw-framework`     = "org.tmt" %% "csw-framework"     % Version
  val `csw-command`       = "org.tmt" %% "csw-command"       % Version
  val `csw-messages`      = "org.tmt" %% "csw-messages"      % Version
  val `csw-event-api`     = "org.tmt" %% "csw-event-api"     % Version
  val `csw-event-client`  = "org.tmt" %% "csw-event-client"  % Version
  val `csw-event-impl`    = "org.tmt" %% "csw-event-impl"    % Version

}