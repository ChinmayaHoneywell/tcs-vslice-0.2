lazy val `pk-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.PkAssembly
  )

lazy val `pk-client` = project
  .settings(
    libraryDependencies ++= Dependencies.PkClient
  )

lazy val `pk-deploy` = project
  .dependsOn(
    `pk-assembly`,
    `pk-client`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.PkDeploy
  )
