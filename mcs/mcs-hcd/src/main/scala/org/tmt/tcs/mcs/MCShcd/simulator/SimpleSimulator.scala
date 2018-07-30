package org.tmt.tcs.mcs.MCShcd.simulator

import com.typesafe.config.Config
import csw.messages.commands.{CommandResponse, ControlCommand}
import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.msgTransformers.SubystemResponse

object SimpleSimulator {
  def create(loggerFactory: LoggerFactory) = SimpleSimulator(loggerFactory)
}
case class SimpleSimulator(loggerFactory: LoggerFactory) extends Simulator {

  private val log = loggerFactory.getLogger
  override def initializeSimulator(config: Config) = {
    Thread.sleep(50)
    log.info(msg = s"Successfully Initialized simple simulator")
  }
  override def submitCommand(controlCommand: ControlCommand): Option[Boolean] = {
    Thread.sleep(100)
    log.info(msg = s"Successfully processed command : ${controlCommand.commandName} in  simple simulator")
    Some(true)
  }
  override def readCommandResponse(commandName: String): Option[SubystemResponse] = {
    log.info(msg = s"In simple simulator reading response for  command : ${commandName}")
    Thread.sleep(150)
    Some(SubystemResponse(true, None, None))
  }

}
