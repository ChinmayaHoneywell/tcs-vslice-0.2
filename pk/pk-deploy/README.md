# pk-deploy

This module contains apps and configuration files for host deployment using 
HostConfig (https://tmtsoftware.github.io/csw-prod/apps/hostconfig.html) and 
ContainerCmd (https://tmtsoftware.github.io/csw-prod/framework/deploying-components.html).

An important part of making this work is ensuring the host config app (PkHostConfigApp) is built
with all of the necessary dependencies of the components it may run.  This is done by adding settings to the
built.sbt file:

```
lazy val `pk-deploy` = project
  .dependsOn(
    `pk-assembly`,
    `pk-hcd`
  )
  .enablePlugins(JavaAppPackaging)
  .settings(
    libraryDependencies ++= Dependencies.PkDeploy
  )
```

and in Libs.scala:

```

  val `csw-framework`  = "org.tmt" %% "csw-framework"  % Version

```

To start pk Assembly and HCD, follow below steps:

 - Run `sbt pk-deploy/universal:packageBin`, this will create self contained zip in target/universal directory
 - Unzip generate zip and enter into bin directory
 - Run container cmd script or host config app script
 - Ex.  `./pk-host-config-app --local ../../../../pk-deploy/src/main/resources/PkHostConfig.conf -s ./pk-container-cmd-app`

Note: the CSW Location Service cluster seed must be running, and appropriate environment variables set to run apps.
See https://tmtsoftware.github.io/csw-prod/apps/cswclusterseed.html .