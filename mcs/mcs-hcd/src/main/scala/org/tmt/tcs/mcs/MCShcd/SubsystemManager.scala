package org.tmt.tcs.mcs.MCShcd

import com.typesafe.config.Config
import csw.messages.commands.CommandIssue.WrongInternalStateIssue
import csw.messages.commands.{CommandIssue, CommandResponse, ControlCommand}

import csw.messages.params.models.Id
import csw.services.logging.scaladsl.LoggerFactory

import org.tmt.tcs.mcs.MCShcd.msgTransformers.SubystemResponse
import org.tmt.tcs.mcs.MCShcd.simulator.{Simulator}

object SubsystemManager {
  def create(loggerFactory: LoggerFactory): SubsystemManager = SubsystemManager(loggerFactory)
}
case class SubsystemManager(loggerFactory: LoggerFactory) {
  private val log                  = loggerFactory.getLogger
  var simulator: Option[Simulator] = None

  def initialize(config: Config, simulator: Simulator): Unit = {
    this.simulator = Some(simulator)
    this.simulator.get.initializeSimulator(config)
  }

  def sendCommand(controlCommand: ControlCommand): CommandResponse = {

    val status: Option[Boolean] = simulator.get.submitCommand(controlCommand)
    if (status.get) {
      val response: Option[SubystemResponse] = simulator.get.readCommandResponse(controlCommand.commandName.name)
      return processCommandResponse(controlCommand.runId, response)
    }
    CommandResponse.Error(
      controlCommand.runId,
      s"Unable to submit command : ${controlCommand.runId} and name : ${controlCommand.commandName} to subsystem"
    )
  }
  def processCommandResponse(runID: Id, subsystemResponse: Option[SubystemResponse]): CommandResponse = {
    subsystemResponse match {
      case Some(response) => {
        response.commandResponse match {
          case true  => return CommandResponse.Completed(runID)
          case false => return decodeErrorState(runID, response)
        }
      }
      case _ => {
        return CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
      }
    }
    CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
  }
  def decodeErrorState(runID: Id, response: SubystemResponse): CommandResponse = {
    response.errorReason.get match {
      case "ILLEGAL_STATE" => {
        return CommandResponse.Invalid(runID, WrongInternalStateIssue(response.errorInfo.get))
      }
      case "BUSY" => {
        return CommandResponse.NotAllowed(runID, CommandIssue.OtherIssue(response.errorInfo.get))
      }
      case "OUT_OF_RANGE" => {
        return CommandResponse.Invalid(runID, CommandIssue.ParameterValueOutOfRangeIssue(response.errorInfo.get))
      }
      case "OUT_OF_SPEC" => {
        return CommandResponse.Invalid(runID, CommandIssue.WrongParameterTypeIssue(response.errorInfo.get))
      }
      case "FAILED" => {
        return CommandResponse.Error(runID, response.errorInfo.get)
      }
      case _ => return CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
    }
    CommandResponse.Invalid(runID, CommandIssue.UnsupportedCommandInStateIssue("unknown command send"))
  }

}
