package dev.skylyrics.core;

public record LyricCue(long startMs, long endMs, String text) {
    public LyricCue {
        if (startMs < 0 || endMs <= startMs || text == null) {
            throw new IllegalArgumentException("Invalid lyric interval");
        }
    }
}
