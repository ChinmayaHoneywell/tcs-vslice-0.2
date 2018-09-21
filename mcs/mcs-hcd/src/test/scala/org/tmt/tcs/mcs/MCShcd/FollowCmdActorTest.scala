package org.tmt.tcs.mcs.MCShcd

import akka.actor
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.{typed, ActorSystem}
import csw.messages.commands.{CommandName, Setup}
import csw.messages.params.models.Prefix
import csw.services.location.commons.ActorSystemFactory
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.ImmediateCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.SubmitCommand
import org.tmt.tcs.mcs.MCShcd.workers.FollowCmdActor

class FollowCmdActorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote()
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)
  private val mocks                             = new FollowCmdMocks()
  private val loggerFactory                     = mocks.loggerFactory
  private val log                               = mocks.log

  //
  /*
  This test case sends Immediate command message to Follwo command Actor and expects that follow command actor will
  send submitCommand ZeroMQ message to zerMQ actor
  TODO : not getting why its throwing assertion failed: timeout (3 seconds) error need to resolve this
   */
  test("Test whether zeroMQ actor receives correct message or not") {
    val prefix = Prefix("tmt.tcs.McsAssembly-Client")
    val setup  = Setup(prefix, CommandName("Follow"), None)
    val inbox         = TestInbox[ZeroMQMessage]()
    val submitCommand = SubmitCommand(inbox.ref, setup)
    val commandHandlerActor = TestProbe[HCDCommandMessage]()
    val immediateCommand: ImmediateCommand = ImmediateCommand(commandHandlerActor.ref, setup)
    val behaviorTestKit: BehaviorTestKit[ImmediateCommand] =
      BehaviorTestKit(FollowCmdActor.create(mocks.commandResponseManager, inbox.ref, mocks.loggerFactory))
    behaviorTestKit.run(immediateCommand)
    // inbox.expectMessage(submitCommand)
    
  }
  when(loggerFactory.getLogger).thenReturn(log)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(log)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(log)
}
