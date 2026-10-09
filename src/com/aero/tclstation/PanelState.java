package com.aero.tclstation;

/** Interaction model shared by all dashboard cards, independent of Android views. */
public final class PanelState {
    private final int count;
    private final long doubleTapMillis;
    private int expanded = -1;
    private int lastPanel = -1;
    private long lastTap = Long.MIN_VALUE;

    public PanelState(int count, long doubleTapMillis) {
        if (count < 1 || doubleTapMillis < 1) throw new IllegalArgumentException("Invalid panel state configuration");
        this.count = count;
        this.doubleTapMillis = doubleTapMillis;
    }

    public int expanded() { return expanded; }

    public void restore(int index) {
        expanded = index >= 0 && index < count ? index : -1;
        lastPanel = -1;
        lastTap = Long.MIN_VALUE;
    }

    public void tap(int index, long elapsedMillis) {
        if (index < 0 || index >= count) return;
        if (expanded == index && lastPanel == index && elapsedMillis >= lastTap
                && elapsedMillis - lastTap <= doubleTapMillis) {
            expanded = -1;
            lastPanel = -1;
            lastTap = Long.MIN_VALUE;
        } else {
            expanded = index;
            lastPanel = index;
            lastTap = elapsedMillis;
        }
    }
}
