package dev.skylyrics.core;

import java.util.List;
import java.util.function.BiFunction;

/** Seeks change the active musical cue. Camera motion is deliberately absent. */
public final class MusicLyricsController {
    private List<LyricCue> cues = List.of();
    private int consumed = -2;
    private WorldPhrase current;

    public void replace(List<LyricCue> cues, long nowNs) {
        if (current != null) current.finish(nowNs);
        this.cues = List.copyOf(cues);
        consumed = -2;
        current = null;
    }

    public void suspend(long nowNs) {
        if (current != null) current.finish(nowNs);
        current = null;
    }

    public void tick(long positionMs, long nowNs, double rate, boolean enabled,
                     BiFunction<LyricCue, Long, WorldPhrase> spawn) {
        int low = 0, high = cues.size() - 1, index = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (cues.get(mid).startMs() <= positionMs) { index = mid; low = mid + 1; }
            else high = mid - 1;
        }
        if (index >= 0 && positionMs >= cues.get(index).endMs()) index = -1;
        if (index != consumed) {
            if (current != null) current.finish(nowNs);
            consumed = index;
            current = null;
            if (enabled && index >= 0 && !cues.get(index).text().isBlank()) {
                long remaining = remaining(cues.get(index), positionMs, rate);
                current = spawn.apply(cues.get(index), remaining);
            }
        }
        if (current != null && index >= 0) {
            // Reconcile timing on the SAME appearance. No repositioning or respawning.
            current.alignDeadline(nowNs, remaining(cues.get(index), positionMs, rate));
        }
    }

    private static long remaining(LyricCue cue, long position, double rate) {
        return Math.max(1, (long) ((cue.endMs() - position) / (rate > 0 ? rate : 1)));
    }
}
