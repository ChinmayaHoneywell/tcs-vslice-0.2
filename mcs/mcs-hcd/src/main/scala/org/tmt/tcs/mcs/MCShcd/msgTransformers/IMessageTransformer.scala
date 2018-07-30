package org.tmt.tcs.mcs.MCShcd.msgTransformers

import csw.messages.commands.ControlCommand

case class SubystemResponse(commandResponse: Boolean, errorReason: Option[String], errorInfo: Option[String])
trait IMessageTransformer {
  def decode(responsePacket: Array[Byte]): SubystemResponse

  def encodeMessage(controlCommand: ControlCommand): Array[Byte]
}
