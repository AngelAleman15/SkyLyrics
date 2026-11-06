package dev.skylyrics.lyrics;

import com.google.gson.*;
import dev.skylyrics.core.PlaybackSnapshot;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** Exact lookup, bounded fallback search and auditable conservative selection. Worker thread only. */
public final class LrclibProvider implements LyricsProvider {
    public record Response(int status, String contentType, String body, String retryAfter) { }
    public interface Transport { Response get(URI uri, String userAgent) throws IOException, InterruptedException; }
    private static final Gate GLOBAL_GATE = new Gate();
    private static final class Gate { long untilMs, previousMs; }
    private final Gate gate;
    private final LyricsCache cache;
    private final Supplier<String> identity;
    private final Transport transport;
    private final boolean live;
    private record Choice(JsonObject record, TrackMatcher.Assessment match, LyricsResult result) { }
    private static final class Failure extends Exception {
        final LyricsResult.State state; final long retry;
        Failure(LyricsResult.State state, String message, long retry) { super(message); this.state = state; this.retry = retry; }
    }
    public LrclibProvider(LyricsCache cache, Supplier<String> identity) {
        this(cache, identity, httpTransport(), GLOBAL_GATE, true);
    }
    public LrclibProvider(LyricsCache cache, Supplier<String> identity, Transport transport) {
        this(cache, identity, transport, new Gate(), false);
    }
    private LrclibProvider(LyricsCache cache, Supplier<String> identity, Transport transport, Gate gate, boolean live) {
        this.cache = cache; this.identity = identity; this.transport = transport; this.gate = gate; this.live = live;
    }
    private static Transport httpTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
        return (uri, agent) -> {
            var request = HttpRequest.newBuilder(uri).header("User-Agent", agent).header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                    response.body(), response.headers().firstValue("Retry-After").orElse(""));
        };
    }
    @Override public LyricsResult find(PlaybackSnapshot track) throws Exception {
        var attempts = new ArrayList<LyricsDiagnostics.Attempt>();
        var candidates = new ArrayList<LyricsDiagnostics.Candidate>();
        LyricsResult cached = cache.read(track);
        if (cached != null) return cached.withDiagnostics(cached.diagnostics().fromCache());
        String contact = identity.get().strip();
        if (contact.isBlank() || contact.contains("\n") || contact.contains("\r")) return outcome(track,
                LyricsResult.empty(LyricsResult.State.CONFIGURE_IDENTITY), "CONFIG", "Configure lrclibIdentity", attempts, candidates);
        if (track.title().isBlank() || track.artist().isBlank() || track.durationMs() < 1000 || track.durationMs() > 3_600_000) return outcome(track,
                LyricsResult.empty(LyricsResult.State.INVALID_METADATA), "METADATA", "Title/artist or duration missing/out of range", attempts, candidates);
        gate.untilMs = Math.max(gate.untilMs, cache.cooldownUntil());
        if (System.currentTimeMillis() < gate.untilMs) return outcome(track,
                new LyricsResult(LyricsResult.State.RATE_LIMITED, List.of(), List.of(), LyricsDiagnostics.empty(), gate.untilMs - System.currentTimeMillis()),
                "COOLDOWN", "Retry-After still active", attempts, candidates);
        Map<Long, Choice> choices = new LinkedHashMap<>();
        LyricsResult fallback = null;
        try {
            String exact = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist())
                    + "&album_name=" + encode(track.album()) + "&duration=" + String.format(Locale.ROOT, "%.3f", track.durationMs() / 1000.0);
            JsonElement record = request("get_exact", "/api/get?" + exact, contact, attempts);
            if (record != null) {
                if (!record.isJsonObject()) throw new Failure(LyricsResult.State.MALFORMED_RESPONSE, "get did not return an object", 0);
                Choice choice = examine(track, record.getAsJsonObject(), candidates);
                if (choice.match.accepted()) {
                    choices.put(id(choice.record), choice);
                    if (choice.result.state() == LyricsResult.State.SYNCED || choice.result.state() == LyricsResult.State.INSTRUMENTAL) {
                        return store(track, choice.result, "GET_EXACT", "Selected record " + id(choice.record), attempts, candidates);
                    }
                    fallback = choice.result;
                }
            }
            LinkedHashSet<String> queries = new LinkedHashSet<>();
            String artist = TrackMatcher.primaryArtist(track.artist());
            queries.add("track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist()));
            queries.add("track_name=" + encode(TrackMatcher.baseTitle(track.title())) + "&artist_name=" + encode(artist));
            queries.add("q=" + encode(TrackMatcher.baseTitle(track.title()) + " " + artist));
            for (String query : queries) {
                JsonElement results = request("search", "/api/search?" + query, contact, attempts);
                if (results == null) continue;
                if (!results.isJsonArray()) throw new Failure(LyricsResult.State.MALFORMED_RESPONSE, "search did not return an array", 0);
                int count = 0;
                for (JsonElement item : results.getAsJsonArray()) {
                    if (++count > 20) break;
                    if (!item.isJsonObject()) continue;
                    JsonObject candidate = item.getAsJsonObject();
                    if (choices.containsKey(id(candidate))) continue;
                    Choice choice = examine(track, candidate, candidates);
                    if (choice.match.accepted()) choices.put(id(candidate), choice);
                }
                List<Choice> timed = choices.values().stream().filter(c -> c.result.state() == LyricsResult.State.SYNCED)
                        .sorted(Comparator.<Choice>comparingDouble(c -> c.match.score()).reversed()
                                .thenComparing(Comparator.comparingInt((Choice c) -> c.result.words().size() > 0 ? 1 : 0).reversed())).toList();
                if (!timed.isEmpty()) {
                    Choice best = timed.get(0);
                    if (timed.size() > 1 && Math.abs(best.match.score() - timed.get(1).match.score()) < .75
                            && !TrackMatcher.canonical(str(best.record, "albumName")).equals(TrackMatcher.canonical(str(timed.get(1).record, "albumName")))) {
                        return outcome(track, LyricsResult.empty(LyricsResult.State.AMBIGUOUS_MATCH), "SEARCH",
                                "Several editions have similar scores; import an LRC for this track", attempts, candidates);
                    }
                    return store(track, best.result, "SEARCH", "Selected record " + id(best.record), attempts, candidates);
                }
            }
            if (fallback == null) fallback = choices.values().stream().map(Choice::result).findFirst()
                    .orElse(LyricsResult.empty(candidates.isEmpty() ? LyricsResult.State.NOT_FOUND : LyricsResult.State.NO_SAFE_MATCH));
            return store(track, fallback, "SEARCH", fallback.state() == LyricsResult.State.PLAIN_ONLY
                    ? "Lyrics exist, but have no timestamps" : "No candidate passed title/artist/version/duration checks", attempts, candidates);
        } catch (Failure failure) {
            return outcome(track, new LyricsResult(failure.state, List.of(), List.of(), LyricsDiagnostics.empty(), failure.retry),
                    "REQUEST_FAILED", failure.getMessage(), attempts, candidates);
        } catch (RuntimeException invalid) {
            return outcome(track, LyricsResult.empty(LyricsResult.State.MALFORMED_RESPONSE), "INVALID_RESPONSE",
                    invalid.getClass().getSimpleName() + ": " + Objects.toString(invalid.getMessage(), "Invalid provider data"), attempts, candidates);
        }
    }
    private JsonElement request(String stage, String query, String contact, List<LyricsDiagnostics.Attempt> attempts) throws InterruptedException, Failure {
        for (int retry = 0; retry < 3; retry++) {
            if (live) Thread.sleep(Math.max(0, gate.previousMs + 250 - System.currentTimeMillis()));
            Response response;
            try { response = transport.get(URI.create("https://lrclib.net" + query), "SkyLyrics/0.4.0 (" + contact + ")"); }
            catch (IOException e) {
                attempts.add(new LyricsDiagnostics.Attempt(stage, query, 0, e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "connection failed")));
                if (retry == 2) throw new Failure(LyricsResult.State.NETWORK_ERROR, "Connection failed after 3 attempts: " + e.getClass().getSimpleName(), 1000);
                if (live) Thread.sleep(500L << retry);
                continue;
            }
            gate.previousMs = System.currentTimeMillis();
            attempts.add(new LyricsDiagnostics.Attempt(stage, query, response.status, ""));
            if (response.status == 429 || response.status == 503) {
                long delay = retryAfter(response.retryAfter);
                gate.untilMs = System.currentTimeMillis() + delay;
                cache.saveCooldown(gate.untilMs);
                throw new Failure(LyricsResult.State.RATE_LIMITED, "HTTP " + response.status + "; Retry-After=" + delay + " ms", delay);
            }
            if (response.status >= 500 && retry < 2) { if (live) Thread.sleep(500L << retry); continue; }
            if (response.status == 404) return null;
            if (response.status != 200) throw new Failure(LyricsResult.State.HTTP_ERROR, "HTTP " + response.status, response.status >= 500 ? 1000 : 0);
            if (!response.contentType.toLowerCase(Locale.ROOT).contains("json") || response.body.length() > 4_000_000) throw new Failure(LyricsResult.State.MALFORMED_RESPONSE, "Invalid content type or response too large", 0);
            try { return JsonParser.parseString(response.body); }
            catch (RuntimeException e) { throw new Failure(LyricsResult.State.MALFORMED_RESPONSE, "Invalid JSON response", 0); }
        }
        throw new Failure(LyricsResult.State.HTTP_ERROR, "Server unavailable", 1000);
    }
    private Choice examine(PlaybackSnapshot track, JsonObject record, List<LyricsDiagnostics.Candidate> diagnostics) throws Failure {
        String title = str(record, "trackName"), artist = str(record, "artistName"), album = str(record, "albumName");
        double duration;
        try { duration = record.get("duration").getAsDouble(); }
        catch (RuntimeException e) { throw new Failure(LyricsResult.State.MALFORMED_RESPONSE, "Missing candidate duration", 0); }
        var match = TrackMatcher.assess(track, title, artist, album, duration);
        diagnostics.add(new LyricsDiagnostics.Candidate(id(record), title, artist, album, duration, match.accepted(), match.score(), match.reason()));
        LyricsResult result = match.accepted() ? decode(record, track.durationMs()) : LyricsResult.empty(LyricsResult.State.NOT_FOUND);
        return new Choice(record, match, result);
    }
    private LyricsResult decode(JsonObject record, long duration) {
        if (record.has("instrumental") && record.get("instrumental").getAsBoolean()) return LyricsResult.empty(LyricsResult.State.INSTRUMENTAL);
        LyricsfileParser.Parsed yaml = null;
        String note = "";
        if (!str(record, "lyricsfile").isBlank()) {
            try {
                yaml = LyricsfileParser.parse(str(record, "lyricsfile"), duration);
                if (!yaml.words().isEmpty()) return new LyricsResult(LyricsResult.State.SYNCED, yaml.cues(), yaml.words(),
                        new LyricsDiagnostics("", "", "", 0, "LYRICSFILE", "WORD", "Explicit word timestamps retained", List.of(), List.of(), false), 0);
            } catch (RuntimeException e) { note = "Lyricsfile rejected: " + e.getMessage(); }
        }
        var lrc = LrcParser.parse(str(record, "syncedLyrics"), duration);
        if (!lrc.isEmpty()) return new LyricsResult(LyricsResult.State.SYNCED, lrc, List.of(),
                new LyricsDiagnostics("", "", "", 0, "SYNCED_LRC", "LINE", note, List.of(), List.of(), false), 0);
        if (yaml != null && !yaml.cues().isEmpty()) return new LyricsResult(LyricsResult.State.SYNCED, yaml.cues(), List.of(),
                new LyricsDiagnostics("", "", "", 0, "LYRICSFILE", "LINE", note, List.of(), List.of(), false), 0);
        return LyricsResult.empty(!str(record, "plainLyrics").isBlank() ? LyricsResult.State.PLAIN_ONLY : LyricsResult.State.NO_SYNCED_LYRICS)
                .withDiagnostics(new LyricsDiagnostics("", "", "", 0, "NO_TIMING", "NONE", note, List.of(), List.of(), false));
    }
    private LyricsResult store(PlaybackSnapshot track, LyricsResult result, String source, String detail, List<LyricsDiagnostics.Attempt> attempts, List<LyricsDiagnostics.Candidate> candidates) {
        result = outcome(track, result, source, detail, attempts, candidates);
        try { cache.writeResult(track, result); } catch (Exception ignored) { /* A cache failure must not erase valid lyrics. */ }
        return result;
    }
    private LyricsResult outcome(PlaybackSnapshot track, LyricsResult result, String source, String detail,
                                 List<LyricsDiagnostics.Attempt> attempts, List<LyricsDiagnostics.Candidate> candidates) {
        return result.withDiagnostics(new LyricsDiagnostics(track.title(), track.artist(), track.album(), track.durationMs(),
                source + (result.diagnostics().source().isBlank() ? "" : "/" + result.diagnostics().source()),
                result.diagnostics().syncMode(), detail + "; " + result.diagnostics().detail(), attempts, candidates, false));
    }
    static long retryAfter(String value) {
        try { return Math.max(1000, Math.min(7 * 86_400_000L, Long.parseLong(value) * 1000)); }
        catch (Exception ignored) {
            try { return Math.max(1000, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
            catch (Exception e) { return 60_000; }
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String str(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : ""; }
    private static long id(JsonObject object) { return object.has("id") ? object.get("id").getAsLong() : Objects.hash(str(object, "trackName"), str(object, "artistName"), str(object, "albumName")); }
}
