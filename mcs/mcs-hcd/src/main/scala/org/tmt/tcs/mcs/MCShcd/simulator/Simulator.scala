package org.tmt.tcs.mcs.MCShcd.simulator

import com.typesafe.config.Config
import csw.messages.commands.ControlCommand
import org.tmt.tcs.mcs.MCShcd.msgTransformers.SubystemResponse

case class CurrentPosition(azPos: Double, elPos: Double, asPosError: Double, elPosError: Double)
trait Simulator {

  def initializeSimulator(config: Config)
  def submitCommand(controlCommand: ControlCommand): Option[Boolean]
  def readCommandResponse(commandName: String): Option[SubystemResponse]
  def publishCurrentPosition(): CurrentPosition
}
