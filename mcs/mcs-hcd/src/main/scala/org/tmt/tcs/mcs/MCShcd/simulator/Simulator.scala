package org.tmt.tcs.mcs.MCShcd.simulator

import csw.messages.commands.{CommandResponse, ControlCommand}

trait Simulator {

  def submitCommand(controlCommand: ControlCommand): CommandResponse
}
