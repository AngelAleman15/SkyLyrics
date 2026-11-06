package dev.skylyrics.core;

import java.util.List;
import java.util.function.Consumer;

/** Phase-1 monotonic demo timeline. Each cue is consumed once, even while hidden. */
public final class LyricsSynchronizer {
    private final List<LyricCue> cues;
    private long originNs;
    private int next;
    private boolean running;

    public LyricsSynchronizer(List<LyricCue> cues) {
        this.cues = List.copyOf(cues);
        long end = -1;
        for (LyricCue cue : cues) {
            if (cue.startMs() < end) throw new IllegalArgumentException("Overlapping cues");
            end = cue.endMs();
        }
    }

    public void start(long nowNs) { originNs = nowNs; next = 0; running = true; }
    public void stop() { running = false; }
    public boolean running() { return running; }

    public void tick(long nowNs, boolean enabled, Consumer<LyricCue> onStart) {
        if (!running) return;
        long position = Math.max(0, (nowNs - originNs) / 1_000_000L);
        while (next < cues.size() && cues.get(next).startMs() <= position) {
            LyricCue cue = cues.get(next++);
            if (enabled && position < cue.endMs()) {
                onStart.accept(new LyricCue(position, cue.endMs(), cue.text()));
            }
        }
        if (next == cues.size() && (cues.isEmpty()
                || position >= cues.get(cues.size() - 1).endMs())) running = false;
    }
}
