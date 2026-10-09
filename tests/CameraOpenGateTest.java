package com.aero.tclstation;

/** Pure-Java checks for asynchronous camera open ownership. */
public final class CameraOpenGateTest {
    public static void main(String[] args) {
        CameraOpenGate gate = new CameraOpenGate();
        int first = gate.begin();
        if (first < 0) throw new AssertionError("first open refused");
        if (gate.begin() >= 0) throw new AssertionError("duplicate in-flight open accepted");
        if (!gate.isCurrent(first)) throw new AssertionError("current callback refused");
        gate.invalidate();
        if (gate.isCurrent(first)) throw new AssertionError("late callback accepted after close");
        int second = gate.begin();
        if (second == first || second < 0) throw new AssertionError("new open failed after close");
        if (gate.isCurrent(first)) throw new AssertionError("old callback owns new camera");
        if (!gate.isCurrent(second)) throw new AssertionError("new callback refused");
        gate.invalidate();
        System.out.println("PASS duplicate camera opens blocked and stale callbacks rejected");
    }
}
