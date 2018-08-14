package org.tmt.tcs.mcs.MCShcd.simulator

import com.typesafe.config.Config
import csw.messages.commands.ControlCommand
import csw.messages.params.generics.Parameter

import csw.services.logging.scaladsl.LoggerFactory
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.msgTransformers.SubystemResponse

object SimpleSimulator {
  def create(loggerFactory: LoggerFactory) = SimpleSimulator(loggerFactory)
}
case class SimpleSimulator(loggerFactory: LoggerFactory) extends Simulator {

  private val log                               = loggerFactory.getLogger
  private var isPublishCurrentPosition: Boolean = false
  private val positionIncr: Double              = 1.0

  private var azCurrent: Double  = 0.0
  private var elCurrent: Double  = 0.0
  private var azDemanded: Double = 180.0
  private var elDemanded: Double = 90.0

  override def initializeSimulator(config: Config) = {
    Thread.sleep(50)
    log.info(msg = s"Successfully Initialized simple simulator")
  }
  override def submitCommand(controlCommand: ControlCommand): Option[Boolean] = {
    Thread.sleep(100)

    val commandName = controlCommand.commandName.name
    if (commandName.equals(Commands.FOLLOW) || commandName.equals(Commands.POINT_DEMAND)) {
      isPublishCurrentPosition = true
      if (commandName.equals(Commands.POINT_DEMAND)) {
        val azParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "AZ").get
        val param1                = azParam.head.asInstanceOf[Double]
        val elParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "EL").get
        val param2                = elParam.head.asInstanceOf[Double]
        azDemanded = param1
        elDemanded = param2
      }
    } else {
      isPublishCurrentPosition = false
    }
    log.info(msg = s"Successfully processed command : ${controlCommand.commandName} in  simple simulator")
    Some(true)
  }
  override def readCommandResponse(commandName: String): Option[SubystemResponse] = {
    log.info(msg = s"In simple simulator reading response for  command : ${commandName}")
    Thread.sleep(150)
    Some(SubystemResponse(true, None, None))
  }
  override def publishCurrentPosition(): CurrentPosition = {
    if (isPublishCurrentPosition) {
      if (azCurrent < azDemanded) {
        azCurrent += positionIncr
      }
      if (elCurrent < elDemanded) {
        elCurrent += positionIncr
      }
    }
    CurrentPosition(azCurrent, elCurrent, azDemanded - azCurrent, elDemanded - elCurrent)
  }
}
