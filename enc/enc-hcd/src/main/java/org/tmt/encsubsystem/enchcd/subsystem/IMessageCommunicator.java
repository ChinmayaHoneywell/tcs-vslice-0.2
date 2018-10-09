package org.tmt.encsubsystem.enchcd.subsystem;

public interface IMessageCommunicator {
    FastMoveCommand.Response sendCommand(FastMoveCommand cmd);
    CurrentPosition receiveEventCurrentPosition();
}
