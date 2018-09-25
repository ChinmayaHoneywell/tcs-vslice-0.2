package org.tmt.encsubsystem.enchcd.org.tmt.encsubsystem.enchcd.subsystem;

import csw.messages.commands.Command;
import csw.messages.commands.CommandResponse;

public interface IMessageCommunicator {
    FastMoveCommand.Response sendCommand(FastMoveCommand cmd);
    CurrentPosition receiveEventCurrentPosition();
}
