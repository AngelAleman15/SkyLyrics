package dev.skylyrics.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skylyrics.core.PlaybackSnapshot;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/** Exact metadata lookup only; never picks the first fuzzy search result. */
public final class LrclibProvider implements LyricsProvider {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private final LyricsCache cache;
    private final Supplier<String> identity;
    private static volatile long retryAfterNs;

    public LrclibProvider(LyricsCache cache, Supplier<String> identity) { this.cache = cache; this.identity = identity; }

    @Override public LyricsResult find(PlaybackSnapshot track) throws Exception {
        LyricsResult cached = cache.read(track);
        if (cached != null) return cached;
        String contact = identity.get().strip();
        // A real project URL/contact must be configured; never ship a fictional identity.
        if (contact.isBlank() || contact.contains("\n") || contact.contains("\r")) {
            return LyricsResult.empty(LyricsResult.State.CONFIGURE_IDENTITY);
        }
        if (System.nanoTime() < retryAfterNs) return LyricsResult.empty(LyricsResult.State.RATE_LIMITED);
        if (track.title().isBlank() || track.artist().isBlank() || track.durationMs() <= 0) {
            return LyricsResult.empty(LyricsResult.State.NOT_FOUND);
        }
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist())
                + "&album_name=" + encode(track.album()) + "&duration="
                + String.format(Locale.ROOT, "%.3f", track.durationMs() / 1000.0);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/get?" + query))
                .header("User-Agent", "SkyLyrics/0.3.0 (" + contact + ")")
                .header("Accept", "application/json").timeout(Duration.ofSeconds(10)).GET().build();
        // ofString keeps the request deadline active through the entire body transfer.
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();
        if (body.length() > 2_000_000) return LyricsResult.empty(LyricsResult.State.ERROR);
        if (response.statusCode() == 429) {
            long seconds = 60;
            try { seconds = Long.parseLong(response.headers().firstValue("Retry-After").orElse("60")); }
            catch (NumberFormatException ignored) { }
            retryAfterNs = System.nanoTime() + Math.max(1, Math.min(86400, seconds)) * 1_000_000_000L;
            return LyricsResult.empty(LyricsResult.State.RATE_LIMITED);
        }
        if (response.statusCode() == 404) {
            cache.write(track, LyricsResult.State.NOT_FOUND, null);
            return LyricsResult.empty(LyricsResult.State.NOT_FOUND);
        }
        if (response.statusCode() != 200) return LyricsResult.empty(LyricsResult.State.ERROR);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!matches(track.title(), json.get("trackName").getAsString())
                || !matches(track.artist(), json.get("artistName").getAsString())
                || Math.abs(track.durationMs() / 1000.0 - json.get("duration").getAsDouble()) > 2
                || (!track.album().isBlank() && !matches(track.album(), json.get("albumName").getAsString()))) {
            return LyricsResult.empty(LyricsResult.State.NOT_FOUND);
        }
        boolean instrumental = json.get("instrumental").getAsBoolean();
        String lrc = json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()
                ? json.get("syncedLyrics").getAsString() : null;
        var cues = instrumental ? java.util.List.<dev.skylyrics.core.LyricCue>of() : LrcParser.parse(lrc, track.durationMs());
        LyricsResult.State state = instrumental ? LyricsResult.State.INSTRUMENTAL
                : cues.isEmpty() ? LyricsResult.State.NO_SYNCED_LYRICS : LyricsResult.State.SYNCED;
        cache.write(track, state, lrc);
        return new LyricsResult(state, cues);
    }

    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static boolean matches(String a, String b) { return a.strip().equalsIgnoreCase(b.strip()); }
}
