package com.aero.tclstation;

/** Single-owner gate for asynchronous camera opens and their late callbacks. */
final class CameraOpenGate {
    private int generation;
    private boolean reserved;

    int begin() {
        if (reserved) return -1;
        reserved = true;
        return generation;
    }

    boolean isCurrent(int token) {
        return reserved && generation == token;
    }

    void invalidate() {
        generation++;
        reserved = false;
    }
}
