package dev.skylyrics.lyrics;

import dev.skylyrics.core.PlaybackSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LyricsCacheTest {
    @TempDir Path folder;
    private final PlaybackSnapshot track = new PlaybackSnapshot("T", "A", "Álbum", 6000, 0, false, 1, 0);

    @Test void localLyricsOverrideNegativeCacheAndVersionsUseDifferentKeys() throws Exception {
        var cache = new LyricsCache(folder);
        cache.write(track, LyricsResult.State.NOT_FOUND, null);
        assertEquals(LyricsResult.State.NOT_FOUND, cache.read(track).state());
        Path local = cache.localFile(track);
        Files.createDirectories(local.getParent());
        Files.writeString(local, "[00:01.00]Original\n[00:05.00]");
        assertEquals(LyricsResult.State.SYNCED, cache.read(track).state());
        var other = new PlaybackSnapshot("T", "A", "Otro álbum", 6000, 0, false, 1, 0);
        assertNotEquals(local, cache.localFile(other));
        assertNull(cache.read(other));
    }

    @Test void blankIdentityNeverMakesNetworkRequestAndCachedDataStillWorks() throws Exception {
        var cache = new LyricsCache(folder);
        var provider = new LrclibProvider(cache, () -> "");
        assertEquals(LyricsResult.State.CONFIGURE_IDENTITY, provider.find(track).state());
        cache.write(track, LyricsResult.State.INSTRUMENTAL, null);
        assertEquals(LyricsResult.State.INSTRUMENTAL, provider.find(track).state());
    }
}
