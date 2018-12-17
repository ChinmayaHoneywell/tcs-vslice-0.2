lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `enc-assembly`,
  `enc-hcd`,
  `enc-deploy`
)

lazy val `enc` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

lazy val `enc-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.EncAssembly
  )

lazy val `enc-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.EncHcd
  )

lazy val `enc-deploy` = project
  .dependsOn(
    `enc-assembly`,
    `enc-hcd`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.EncDeploy
  )
