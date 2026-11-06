package dev.skylyrics.lyrics;

import dev.skylyrics.core.PlaybackSnapshot;
import java.util.List;

public record LyricsDiagnostics(String title, String artist, String album, long durationMs,
                                String source, String syncMode, String detail,
                                List<Attempt> attempts, List<Candidate> candidates, boolean cached) {
    public record Attempt(String stage, String query, int httpStatus, String error) { }
    public record Candidate(long id, String title, String artist, String album, double duration,
                            boolean accepted, double score, String reason) { }
    public LyricsDiagnostics { attempts = List.copyOf(attempts); candidates = List.copyOf(candidates); }
    public static LyricsDiagnostics empty() { return new LyricsDiagnostics("", "", "", 0, "", "NONE", "", List.of(), List.of(), false); }
    public static LyricsDiagnostics local(PlaybackSnapshot track, String detail) {
        return new LyricsDiagnostics(track.title(), track.artist(), track.album(), track.durationMs(),
                "LOCAL_LRC", "LINE", detail, List.of(), List.of(), false);
    }
    public LyricsDiagnostics fromCache() { return new LyricsDiagnostics(title, artist, album, durationMs, source, syncMode, detail, attempts, candidates, true); }
}
