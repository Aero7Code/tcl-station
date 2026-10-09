package com.aero.tclstation;

/** Waits for the exact old camera to finish closing before a switch. */
public final class CameraCloseHandoffTest {
    public static void main(String[] args) {
        CameraCloseHandoff handoff = new CameraCloseHandoff();
        Object oldCamera = new Object();
        handoff.await(oldCamera);
        if (handoff.closed(new Object())) throw new AssertionError("unrelated camera allowed switch");
        if (!handoff.closed(oldCamera)) throw new AssertionError("old camera closure did not allow switch");
        if (handoff.closed(oldCamera)) throw new AssertionError("closure allowed duplicate switch");
        handoff.await(oldCamera);
        handoff.cancel();
        if (handoff.closed(oldCamera)) throw new AssertionError("dismissed preview reopened camera");
        System.out.println("PASS camera switch waits for matching onClosed and cancels on dismissal");
    }
}
