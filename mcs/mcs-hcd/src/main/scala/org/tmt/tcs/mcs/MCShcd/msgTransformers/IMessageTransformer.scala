package org.tmt.tcs.mcs.MCShcd.msgTransformers

import csw.messages.commands.ControlCommand
import org.tmt.tcs.mcs.MCShcd.msgTransformers.protos.TcsMcsEventsProtos.McsCurrentPositionEvent

case class SubystemResponse(commandResponse: Boolean, errorReason: Option[String], errorInfo: Option[String])
case class SubscribedEvent(mcsCurrentPosition: McsCurrentPositionEvent)
trait IMessageTransformer {
  def decode(responsePacket: Array[Byte]): SubystemResponse

  def encodeMessage(controlCommand: ControlCommand): Array[Byte]
  def decodeEvent(eventName: String, encodedEventData: Array[Byte]): SubscribedEvent
}
