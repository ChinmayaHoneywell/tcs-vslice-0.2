package org.tmt.tcs.mcs.MCShcd.org.tmt.tcs.mcs.MCShcd.workers
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, typed}
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.Prefix
import csw.services.location.commons.ActorSystemFactory
import org.junit.Before
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.SubmitCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import org.tmt.tcs.mcs.MCShcd.workers.StartupCmdActor
import org.tmt.tcs.mcs.MCShcd.{FollowCmdMocks, HCDCommandMessage}

class StartupCmdActorTest extends FunSuite with Matchers with BeforeAndAfterAll{

  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote()
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  implicit val scheduler                        = system.scheduler

  private val mocks = new FollowCmdMocks()

  private val loggerFactory = mocks.loggerFactory
  private val log           = mocks.log

  @Before
  def setup(): Unit = {}

  test("Test for simple simulator mode "){
    val prefix = Prefix("tmt.tcs.McsAssembly-StartupTest")
    val setup = Setup(prefix, CommandName("Startup"), None)
    //val inbox                              = TestInbox[ZeroMQMessage]()
    val zeroMQActor                        = TestProbe[ZeroMQMessage]()
    val simpleSim = TestProbe[SimpleSimMsg]()
    val simpleSimActor : ActorRef[SimpleSimMsg] = simpleSim.ref
    val behaviorTestKit: BehaviorTestKit[ControlCommand] =
      BehaviorTestKit(StartupCmdActor.create(mocks.commandResponseManager, zeroMQActor.ref,simpleSimActor,"SimpleSimulator", mocks.loggerFactory))

    behaviorTestKit.run(setup)

    behaviorTestKit.
    //zeroMQActor.expectMessage(Duration.create(30, TimeUnit.SECONDS), submitCommand)
    //val childInbox: TestInbox[SimpleSimMsg] = behaviorTestKit.childInbox("$a")

   // childInbox.expectMessage(submitCommand)
  }
}
