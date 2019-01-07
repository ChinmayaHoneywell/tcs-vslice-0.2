name := "MCSSubsystem"

version := "0.1"

scalaVersion := "2.12.6"
scalacOptions ++= Seq("-unchecked", "-deprecation", "-language:postfixOps")
libraryDependencies ++= Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.0",
                          "org.zeromq" % "jeromq" % "0.4.3",
                          "com.typesafe" % "config" % "1.3.1",
                          "com.google.protobuf" % "protobuf-java" % "3.5.1"
                      )
