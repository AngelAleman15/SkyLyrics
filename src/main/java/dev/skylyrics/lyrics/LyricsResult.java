package dev.skylyrics.lyrics;

import dev.skylyrics.core.LyricCue;
import java.util.List;

public record LyricsResult(State state, List<LyricCue> cues, List<WordCue> words,
                           LyricsDiagnostics diagnostics, long retryAfterMs) {
    public enum State { SYNCED, INSTRUMENTAL, NO_SYNCED_LYRICS, PLAIN_ONLY, NOT_FOUND,
        CONFIGURE_IDENTITY, INVALID_METADATA, NO_SAFE_MATCH, AMBIGUOUS_MATCH, NETWORK_ERROR, HTTP_ERROR,
        MALFORMED_RESPONSE, LOCAL_FILE_ERROR, ERROR, RATE_LIMITED }
    public LyricsResult(State state, List<LyricCue> cues) { this(state, cues, List.of(), LyricsDiagnostics.empty(), 0); }
    public LyricsResult { cues = List.copyOf(cues); words = List.copyOf(words); }
    public static LyricsResult empty(State state) { return new LyricsResult(state, List.of()); }
    public LyricsResult withDiagnostics(LyricsDiagnostics diagnostics) { return new LyricsResult(state, cues, words, diagnostics, retryAfterMs); }
}
