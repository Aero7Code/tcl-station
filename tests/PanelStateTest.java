package com.aero.tclstation;

public final class PanelStateTest {
    private static int passed;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        PanelState state = new PanelState(7, 350);
        check(state.expanded() == -1, "all panels start compact");
        state.tap(0, 1000);
        check(state.expanded() == 0, "single tap expands panel immediately");
        state.tap(0, 1200);
        check(state.expanded() == -1, "double tap shrinks panel");
        state.tap(0, 1300);
        check(state.expanded() == 0, "tap after double tap expands again");
        state.tap(0, 2000);
        check(state.expanded() == 0, "slow second tap leaves expanded panel open");
        state.tap(1, 2100);
        check(state.expanded() == 1, "other panel replaces expanded panel");
        state.tap(0, 2200);
        check(state.expanded() == 0, "cross-panel taps do not trigger a double tap");
        state.restore(6);
        check(state.expanded() == 6, "orientation restoration retains selected panel");
        state.restore(7);
        check(state.expanded() == -1, "invalid restored panel is compact");
        state.tap(-1, 2300);
        check(state.expanded() == -1, "invalid tap is ignored");
        state.tap(2, 2400);
        state.tap(2, 2750);
        check(state.expanded() == -1, "tap exactly at threshold shrinks panel");
        System.out.println("PanelStateTest: " + passed + " checks passed");
    }
}
