lazy val `mcs-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.McsAssembly
  )

lazy val `mcs-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.McsHcd
  )

lazy val `mcs-deploy` = project
  .dependsOn(
    `mcs-assembly`,
    `mcs-hcd`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.McsDeploy
  )
