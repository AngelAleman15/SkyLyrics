package dev.skylyrics.lyrics;

import dev.skylyrics.core.PlaybackSnapshot;

public interface LyricsProvider {
    LyricsResult find(PlaybackSnapshot track) throws Exception;
}
