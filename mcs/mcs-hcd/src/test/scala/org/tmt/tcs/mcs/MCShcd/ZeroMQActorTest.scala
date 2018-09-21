package org.tmt.tcs.mcs.MCShcd

import akka.actor
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.{typed, ActorSystem}

import csw.services.location.commons.ActorSystemFactory

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class ZeroMQActorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote()
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  private val mocks = new ZeroMQActorMocks()
  val loggerFactory = mocks.loggerFactory
  val log           = mocks.log

  when(loggerFactory.getLogger).thenReturn(log)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(log)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(log)
}
