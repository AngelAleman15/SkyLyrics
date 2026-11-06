package dev.skylyrics.core;

public record PlaybackSnapshot(String title, String artist, String album, long durationMs,
                               long positionMs, boolean playing, double rate, long receivedNs) {
    public String trackKey() {
        return title + "\u001f" + artist + "\u001f" + album + "\u001f" + durationMs;
    }

    public long positionAt(long nowNs) {
        // A dead bridge must not leave a song extrapolating forever.
        long elapsed = Math.max(0, Math.min(2_000_000_000L, nowNs - receivedNs));
        long position = positionMs + (playing ? (long) (elapsed / 1_000_000.0 * rate) : 0);
        return Math.max(0, durationMs > 0 ? Math.min(durationMs, position) : position);
    }
}
