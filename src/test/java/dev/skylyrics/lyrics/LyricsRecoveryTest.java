package dev.skylyrics.lyrics;

import com.google.gson.*;
import dev.skylyrics.core.PlaybackSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LyricsRecoveryTest {
    @TempDir Path folder;
    private PlaybackSnapshot track(String title, String artist, String album, long duration) {
        return new PlaybackSnapshot(title, artist, album, duration, 0, false, 1, 0);
    }
    private JsonObject record(long id, String title, String artist, String album, double duration, String lrc) {
        JsonObject r = new JsonObject(); r.addProperty("id", id); r.addProperty("trackName", title);
        r.addProperty("artistName", artist); r.addProperty("albumName", album); r.addProperty("duration", duration);
        r.addProperty("instrumental", false); r.addProperty("syncedLyrics", lrc); r.addProperty("plainLyrics", "Original test words");
        return r;
    }
    private LrclibProvider.Response json(int code, String body) { return new LrclibProvider.Response(code, "application/json", body, ""); }
    @Test void normalizesRemasterButPreservesMeaningfulParentheticalTitle() {
        assertEquals("song", TrackMatcher.baseTitle("Song (2011 Remaster)"));
        assertEquals("song", TrackMatcher.baseTitle("Song - 2020 Remastered"));
        assertEquals("sweet dreams are made of this", TrackMatcher.baseTitle("Sweet Dreams (Are Made of This)"));
        var original = track("Song - 2020 Remastered", "Artist", "Reissue", 180000);
        assertTrue(TrackMatcher.assess(original, "Song", "Artist", "Original Album", 181).accepted());
        assertFalse(TrackMatcher.assess(original, "Song (Live)", "Artist", "Live at Test", 180).accepted());
        assertFalse(TrackMatcher.assess(original, "Song", "Cover Singer", "Reissue", 180).accepted());
        assertFalse(TrackMatcher.assess(original, "Song (Club Mix)", "Artist", "Reissue", 180).accepted());
        assertFalse(TrackMatcher.assess(track("Song (Live at X)", "Artist", "Live", 180000),
                "Song (Live at Y)", "Artist", "Live", 180).accepted());
    }
    @Test void collaborationMustMatchAndWrongDurationsAreRejected() {
        var original = track("Song (feat. Guest)", "Lead", "Album", 180000);
        assertTrue(TrackMatcher.assess(original, "Song", "Lead feat. Guest", "Album", 181).accepted());
        assertFalse(TrackMatcher.assess(original, "Song", "Lead", "Album", 180).accepted());
        assertFalse(TrackMatcher.assess(original, "Song", "Lead feat. Guest", "Album", 210).accepted());
        assertTrue(TrackMatcher.assess(track("Song", "Lead & Guest", "Album", 180000), "Song", "Guest, Lead", "Album", 180).accepted());
    }
    @Test void failedExactLookupSearchesAndAuditsRejections() throws Exception {
        var original = track("Song - 2011 Remaster", "Artist", "Reissue", 180000);
        var calls = new AtomicInteger();
        var provider = new LrclibProvider(new LyricsCache(folder), () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) -> {
            calls.incrementAndGet();
            if (uri.getPath().equals("/api/get")) {
                assertTrue(uri.getQuery().contains("2011")); assertTrue(uri.getQuery().contains("album_name=Reissue"));
                return json(404, "{}");
            }
            JsonArray array = new JsonArray();
            array.add(record(1, "Song", "Cover Singer", "Cover Album", 180, "[00:01]Cover"));
            array.add(record(2, "Song (Live)", "Artist", "Live at Test", 180, "[00:01]Live"));
            array.add(record(3, "Song", "Artist", "Original Album", 181, "[00:01]Original test line"));
            return json(200, array.toString());
        });
        var result = provider.find(original);
        assertEquals(LyricsResult.State.SYNCED, result.state()); assertEquals(2, calls.get());
        assertEquals(original.title(), result.diagnostics().title());
        assertEquals(2, result.diagnostics().candidates().stream().filter(c -> !c.accepted()).count());
        assertEquals("Original test line", result.cues().get(0).text());
    }
    @Test void plainLyricsAreNotGivenTimestamps() throws Exception {
        var provider = new LrclibProvider(new LyricsCache(folder), () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) ->
                json(200, uri.getPath().equals("/api/get") ? record(1, "Song", "Artist", "Album", 6, "").toString() : "[]"));
        var result = provider.find(track("Song", "Artist", "Album", 6000));
        assertEquals(LyricsResult.State.PLAIN_ONLY, result.state()); assertTrue(result.cues().isEmpty());
        assertTrue(result.diagnostics().detail().contains("no timestamps"));
    }
    @Test void wordTimedLyricsfileWinsAndUnknownWordEndsStayUnknown() throws Exception {
        JsonObject record = record(1, "Song", "Artist", "Album", 6, "[00:01]LRC fallback");
        record.addProperty("lyricsfile", "version: '1.0'\nmetadata:\n  title: Song\n  artist: Artist\nlines:\n  - text: Hola mundo\n    start_ms: 1000\n    end_ms: 4000\n    words:\n      - text: 'Hola '\n        start_ms: 1000\n        end_ms: 2000\n      - text: mundo\n        start_ms: 2000\n");
        var provider = new LrclibProvider(new LyricsCache(folder), () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) -> json(200, record.toString()));
        var result = provider.find(track("Song", "Artist", "Album", 6000));
        assertEquals("WORD", result.diagnostics().syncMode()); assertEquals("Hola mundo", result.cues().get(0).text());
        assertEquals(2, result.words().size()); assertNull(result.words().get(1).endMs());
        assertEquals(result.words(), new LyricsCache(folder).read(track("Song", "Artist", "Album", 6000)).words());
    }
    @Test void networkFailureNeverBecomesNotFoundAndRetriesAreBounded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var provider = new LrclibProvider(new LyricsCache(folder), () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) -> {
            calls.incrementAndGet(); throw new IOException("offline test");
        });
        var result = provider.find(track("Song", "Artist", "Album", 6000));
        assertEquals(LyricsResult.State.NETWORK_ERROR, result.state()); assertEquals(3, calls.get());
        assertEquals(3, result.diagnostics().attempts().size());
    }
    @Test void retryAfterPersistsAcrossServiceInstances() throws Exception {
        var cache = new LyricsCache(folder);
        var original = track("Song", "Artist", "Album", 6000);
        var provider = new LrclibProvider(cache, () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) -> new LrclibProvider.Response(429, "text/plain", "limited", "60"));
        assertEquals(LyricsResult.State.RATE_LIMITED, provider.find(original).state());
        var next = new LrclibProvider(cache, () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) -> { fail("Cooldown ignored"); return null; });
        assertTrue(next.find(original).retryAfterMs() > 50_000);
    }
    @Test void ambiguousEditionsAreNotAutomaticallyChosen() throws Exception {
        JsonArray array = new JsonArray();
        array.add(record(1, "Song", "Artist", "Album One", 6, "[00:01]One"));
        array.add(record(2, "Song", "Artist", "Album Two", 6, "[00:01]Two"));
        var provider = new LrclibProvider(new LyricsCache(folder), () -> "https://github.com/AngelAleman15/SkyLyrics", (uri, agent) ->
                uri.getPath().equals("/api/get") ? json(404, "{}") : json(200, array.toString()));
        assertEquals(LyricsResult.State.AMBIGUOUS_MATCH, provider.find(track("Song", "Artist", "", 6000)).state());
    }
}
