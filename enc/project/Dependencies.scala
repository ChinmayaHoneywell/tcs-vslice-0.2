import sbt._

object Dependencies {

  val EncAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`powermock-module` % Test,
    Libs.`powermock-api` % Test
  )

  val EncHcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test
  )

  val EncDeploy = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test
  )
}
