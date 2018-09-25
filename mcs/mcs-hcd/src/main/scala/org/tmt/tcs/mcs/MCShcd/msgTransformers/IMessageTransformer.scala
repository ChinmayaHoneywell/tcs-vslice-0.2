package org.tmt.tcs.mcs.MCShcd.msgTransformers

import csw.messages.commands.ControlCommand
import csw.messages.params.states.CurrentState

case class SubystemResponse(commandResponse: Boolean, errorReason: Option[String], errorInfo: Option[String])

trait IMessageTransformer {
  def decodeCommandResponse(responsePacket: Array[Byte]): SubystemResponse

  def encodeMessage(controlCommand: ControlCommand): Array[Byte]
  def decodeEvent(eventName: String, encodedEventData: Array[Byte]): CurrentState
  def encodeEvent(mcsPositionDemands: MCSPositionDemand): Array[Byte]
}
