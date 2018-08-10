package org.tmt.encsubsystem.enchcd.org.tmt.encsubsystem.enchcd.subsystem;

/**
 * This command can be submitted to subsystem using IMessageCommunicator.
 * Example - communicator.sendCommand(new FastMoveCommand(3.2, 6.5))
 */
public class FastMoveCommand {
    private double az, el;

    public FastMoveCommand(double az, double el) {
        this.az = az;
        this.el = el;
    }

    public double getEl() {
        return el;
    }

    public void setEl(double el) {
        this.el = el;
    }

    public double getAz() {
        return az;
    }

    public void setAz(double az) {
        this.az = az;
    }

    public static final class Response{
        public enum Status{
            OK, ERROR
        }

        private Status status;

        private String desc;

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }


    }
}
