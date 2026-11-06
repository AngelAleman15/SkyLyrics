package dev.skylyrics.lyrics;

import dev.skylyrics.core.LyricCue;
import java.util.List;

public record LyricsResult(State state, List<LyricCue> cues) {
    public enum State { SYNCED, INSTRUMENTAL, NO_SYNCED_LYRICS, NOT_FOUND, CONFIGURE_IDENTITY, ERROR, RATE_LIMITED }
    public LyricsResult { cues = List.copyOf(cues); }
    public static LyricsResult empty(State state) { return new LyricsResult(state, List.of()); }
}
