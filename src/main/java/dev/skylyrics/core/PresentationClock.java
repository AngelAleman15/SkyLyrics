package dev.skylyrics.core;

/** Continuous animation clock. Pausing music freezes its interval, never its anchor. */
public final class PresentationClock {
    private long valueNs, sampledNs;
    private boolean paused;

    public PresentationClock(long nowNs) { sampledNs = nowNs; }
    public void update(long nowNs, boolean pause) {
        valueNs = at(nowNs);
        sampledNs = nowNs;
        paused = pause;
    }
    public long at(long nowNs) { return valueNs + (paused ? 0 : Math.max(0, nowNs - sampledNs)); }
}
