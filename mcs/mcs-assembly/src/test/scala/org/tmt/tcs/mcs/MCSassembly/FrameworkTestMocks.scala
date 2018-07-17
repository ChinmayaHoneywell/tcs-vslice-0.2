package org.tmt.tcs.mcs.MCSassembly

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.ActorContext
import akka.{Done, actor, testkit}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.messages.location.Connection.AkkaConnection
import csw.messages.params.models.{Id, Prefix, Subsystem}
import csw.services.command.CommandResponseManager
import csw.services.command.scaladsl.CommandService
import csw.services.location.javadsl.ILocationService
import csw.services.location.models.{AkkaRegistration, RegistrationResult}
import csw.services.location.scaladsl.{LocationService, RegistrationFactory}
import csw.services.logging.messages.LogControlMessages
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.scalatest.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{when, _}
import scala.concurrent.duration._
import scala.concurrent.{Future}


class FrameworkTestMocks(implicit untypedSystem: actor.ActorSystem, system: ActorSystem[Nothing]) extends MockitoSugar {
  val testActor                = testkit.TestProbe("test-probe").testActor
  //AkkaRegistration()
  val nothingProbe: TestProbe[Nothing]        =  TestProbe[Nothing]
  val logCntrlMsgProbe: TestProbe[LogControlMessages]                      = TestProbe[LogControlMessages]
  val akkaRegistration                         = AkkaRegistration(mock[AkkaConnection], Prefix(Subsystem.MCS.toString), nothingProbe.ref, logCntrlMsgProbe.ref)
  val locationService: LocationService         = mock[LocationService]
  val registrationResult: RegistrationResult   = mock[RegistrationResult]
  val registrationFactory: RegistrationFactory = mock[RegistrationFactory]


  when(registrationFactory.akkaTyped(any[AkkaConnection],any[Prefix], any[ActorRef[_]]))
    .thenReturn(akkaRegistration)
  when(locationService.register(akkaRegistration)).thenReturn(Future.successful(registrationResult))
  when(locationService.unregister(any[AkkaConnection])).thenReturn(Future.successful(Done))
  when(locationService.asJava).thenReturn(mock[ILocationService])

  val commandService : CommandService = mock[CommandService] // TODO : needs to find out how to get instance of commandService from location service
  //locationService.
  implicit var duration: Timeout = 10 seconds
  val commandResponse : CommandResponse = CommandResponse.Completed.apply(Id())
  var controlCommand : ControlCommand = mock[ControlCommand]
   def setControlCommand(controlCommand : ControlCommand)(implicit duration : Timeout){
      this.controlCommand = controlCommand
     this.duration = duration
  }
    when(commandService.submitAndSubscribe(controlCommand)(duration)).thenReturn(mockReturn)

  def mockReturn() : Future[CommandResponse]= {
    println("In mock call ")
    Future.successful(commandResponse)
  }

  val commandResponseManager: CommandResponseManager                        = mock[CommandResponseManager]

  val loggerFactory: LoggerFactory = mock[LoggerFactory]
  val logger: Logger               = mock[Logger]

  when(loggerFactory.getLogger).thenReturn(logger)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(logger)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(logger)
}
