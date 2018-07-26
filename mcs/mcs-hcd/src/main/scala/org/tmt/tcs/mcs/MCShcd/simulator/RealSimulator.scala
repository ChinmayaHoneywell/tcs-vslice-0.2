package org.tmt.tcs.mcs.MCShcd.simulator

import csw.messages.commands.{CommandResponse, ControlCommand}

class RealSimulator extends Simulator {
  def submitCommand(controlCommand: ControlCommand): CommandResponse = {
    //log.info(msg = s"In simple simulator processing command : ${controlCommand.runId}")
    Thread.sleep(50)
    //log.info(msg = s"In simple simulator successfully processed command :  ${controlCommand.runId}")
    //CommandResponse()
    CommandResponse.Completed(controlCommand.runId)
  }
}
