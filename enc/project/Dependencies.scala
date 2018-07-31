import sbt._

object Dependencies {

  val EncAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-command`,
    CSW.`csw-location`,
    CSW.`csw-messages`,
    CSW.`csw-logging`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test
  )

  val EncHcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-command`,
    CSW.`csw-location`,
    CSW.`csw-messages`,
    CSW.`csw-logging`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test
  )

  val EncDeploy = Seq(
    CSW.`csw-framework`
  )
}
