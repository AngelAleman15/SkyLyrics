package dev.skylyrics.lyrics;

/** Provider timestamps in milliseconds. A missing end remains unknown, never guessed. */
public record WordCue(int lineIndex, String text, long startMs, Long endMs) { }
