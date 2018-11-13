package org.tmt.tcs.mcs.MCShcd

import akka.actor
import akka.actor.typed.ActorSystem
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.scalatest.mockito.MockitoSugar

class ZeroMQActorMocks(implicit untypedSystem: actor.ActorSystem, system: ActorSystem[Nothing]) extends MockitoSugar {
  val loggerFactory = mock[LoggerFactory]
  val log: Logger   = mock[Logger]
}
