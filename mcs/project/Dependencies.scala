import sbt._

object Dependencies {

  val McsAssembly = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`akka-test` % Test

  )

  val McsHcd = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit`,
    Libs.`zeroMQ`,
    Libs.`protobuf`,
    Libs.`scalatest` % Test,
    Libs.`junit` % Test,
    Libs.`junit-interface` % Test,
    Libs.`mockito-core` % Test,
    Libs.`akka-test` % Test

  )

  val McsDeploy = Seq(
    CSW.`csw-framework`
  )
}
