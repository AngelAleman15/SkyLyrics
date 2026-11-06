package dev.skylyrics.lyrics;

import dev.skylyrics.core.PlaybackSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in external-service smoke test using the example metadata in LRCLIB docs. */
@EnabledIfEnvironmentVariable(named = "SKYLYRICS_LIVE_TESTS", matches = "true")
class LrclibLiveTest {
    @TempDir Path folder;
    @Test void documentedTrackCanBeFetchedAndReadBackFromDiskCache() throws Exception {
        var cache = new LyricsCache(folder);
        var provider = new LrclibProvider(cache, () -> "https://github.com/AngelAleman15/SkyLyrics");
        var track = new PlaybackSnapshot("I Want to Live", "Borislav Slavov",
                "Baldur's Gate 3 (Original Game Soundtrack)", 233000, 0, false, 1, 0);
        var result = provider.find(track);
        assertEquals(LyricsResult.State.SYNCED, result.state());
        assertFalse(result.cues().isEmpty());
        assertEquals(result.cues(), cache.read(track).cues());
        assertEquals(result.words(), cache.read(track).words());
    }
}
