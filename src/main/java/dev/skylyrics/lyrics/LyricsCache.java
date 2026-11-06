package dev.skylyrics.lyrics;

import com.google.gson.Gson;
import dev.skylyrics.core.PlaybackSnapshot;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Disk I/O belongs to the lyrics worker. Negative results expire after one day. */
public final class LyricsCache {
    private record Entry(long cachedAtMs, String key, String state, String lrc) { }
    private static final Gson JSON = new Gson();
    private final Path root;
    public LyricsCache(Path root) { this.root = root; }

    public Path localFile(PlaybackSnapshot track) { return root.resolve("local").resolve(hash(track.trackKey()) + ".lrc"); }

    public LyricsResult read(PlaybackSnapshot track) {
        try {
            Path local = localFile(track);
            if (Files.isRegularFile(local) && Files.size(local) <= 2_000_000) {
                var cues = LrcParser.parse(Files.readString(local), track.durationMs());
                return cues.isEmpty() ? LyricsResult.empty(LyricsResult.State.NO_SYNCED_LYRICS)
                        : new LyricsResult(LyricsResult.State.SYNCED, cues);
            }
            Path path = root.resolve("cache").resolve(hash(track.trackKey()) + ".json");
            if (!Files.isRegularFile(path) || Files.size(path) > 3_000_000) return null;
            Entry entry = JSON.fromJson(Files.readString(path), Entry.class);
            if (!track.trackKey().equals(entry.key)) return null;
            LyricsResult.State state = LyricsResult.State.valueOf(entry.state);
            long age = System.currentTimeMillis() - entry.cachedAtMs;
            if (age < 0 || age > (state == LyricsResult.State.SYNCED ? 30L : 1L) * 86_400_000L) return null;
            return new LyricsResult(state, state == LyricsResult.State.SYNCED
                    ? LrcParser.parse(entry.lrc, track.durationMs()) : java.util.List.of());
        } catch (Exception ignored) { return null; }
    }

    public void write(PlaybackSnapshot track, LyricsResult.State state, String lrc) throws Exception {
        Path folder = root.resolve("cache");
        Files.createDirectories(folder);
        Path temp = Files.createTempFile(folder, "lyrics-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(new Entry(System.currentTimeMillis(), track.trackKey(), state.name(), lrc)));
            Files.move(temp, folder.resolve(hash(track.trackKey()) + ".json"), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
