package com.aero.tclstation;

/** One-shot handoff after a closing camera reports onClosed. */
final class CameraCloseHandoff {
    private Object awaiting;

    void await(Object camera) { awaiting = camera; }

    boolean closed(Object camera) {
        if (awaiting == null || awaiting != camera) return false;
        awaiting = null;
        return true;
    }

    void cancel() { awaiting = null; }
}
