package org.tmt.tcs.mcs.MCSassembly

import akka.actor
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestProbe}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors._
import akka.actor.{typed, ActorSystem}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.testkit.typed.TestKitSettings
import akka.util.Timeout
import csw.messages.commands.CommandResponse.Completed
import csw.messages.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.messages.params.generics.{Key, KeyType, Parameter}
import csw.messages.params.models.Prefix
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar
import scala.concurrent.duration._
import scala.concurrent.Await

class DatumActorTest extends FunSuite with Matchers with MockitoSugar with BeforeAndAfterAll {

  private val actorSystem                        = ActorSystem("test-datumCommandActor")
  implicit val typedSystem: typed.ActorSystem[_] = actorSystem.toTyped
  implicit val testKitSettings: TestKitSettings  = TestKitSettings(typedSystem)
  implicit val timeout: Timeout                  = Timeout(5.seconds)
  override protected def afterAll(): Unit = {
    println("---> In MCSassembly.AbstractActorSpec testing scala programs   <---")
    // Await.result(untypedSystem.terminate(), 5.seconds)
    Await.result(typedSystem.terminate(), 5.seconds)
    Await.result(actorSystem.terminate(), 5.seconds)
  }
  val datumCommandActor: Behavior[ControlCommand] =
    DatumCommandActor.createObject(getMockedCommandResponseManager, getMockedHCDLocation, getMockedLogger)
  //def createBehaviorTestKit1() = BehaviorTestKit(datumCommandActor)
  def createBehaviorTestKit() =
    BehaviorTestKit(  datumCommandActor)
  private def getMockedLogger: LoggerFactory = {
    import org.mockito.Mockito.when
    import org.mockito.ArgumentMatchers.any
    val loggerFactory: LoggerFactory = mock[LoggerFactory]
    val logger: Logger               = mock[Logger]

    // when
    when(loggerFactory.getLogger).thenReturn(logger)
    when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(logger)
    when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(logger)

    loggerFactory
  }
  private def getMockedCommandResponseManager: CommandResponseManager = {
    val commandResponseManager: CommandResponseManager = mock[CommandResponseManager]
    commandResponseManager
  }
  private def getMockedHCDLocation: Option[CommandService] = {
    val hcdLocation: CommandService = mock[CommandService]
    Some(hcdLocation)
  }

  test("When correct parameters are provided DatumCommandHandler Actor respond with command completion response") {

    println("Testing Datum command Actor with correct parameters")
    val behaviorTestKit      = createBehaviorTestKit()
    val commandResponseProbe = TestProbe[CommandResponse]

    val prefix                        = Prefix("tmt.tcs.McsAssembly-testCase")
    val axesKey: Key[String]          = KeyType.StringKey.make("axes")
    val datumParam: Parameter[String] = axesKey.set("BOTH")

    val setup = Setup(prefix, CommandName("Datum"), None).add(datumParam)
    behaviorTestKit.run(setup)
    commandResponseProbe.expectMessage(Completed(setup.runId))

  }

}
