package org.tmt.encsubsystem.enchcd.simplesimulator;

public class CurrentPosition {
    private double az, el;

    public CurrentPosition(double az, double el) {
        this.az = az;
        this.el = el;
    }

    @Override
    public String toString() {
        return "CurrentPosition{" +
                "az=" + az +
                ", el=" + el +
                '}';
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
}
