import sbt._

object Dependencies {

  val PkAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-command`,
    CSW.`csw-location`,
    CSW.`csw-messages`,
    CSW.`csw-logging`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test
  )

  val PkClient = Seq(
    CSW.`csw-framework`,
    CSW.`csw-command`,
    CSW.`csw-location`,
    CSW.`csw-messages`,
    CSW.`csw-logging`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test
  )

  val PkDeploy = Seq(
    CSW.`csw-framework`
  )
}
