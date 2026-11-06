package dev.skylyrics.client;

import dev.skylyrics.core.PlaybackSnapshot;
import dev.skylyrics.lyrics.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

/** All HTTP, cache and local-file reads run off the game/render thread. */
public final class LyricsSession implements AutoCloseable {
    public record Loaded(long revision, String key, LyricsResult result) { }
    private final Path root;
    private final LyricsCache cache;
    private final LyricsProvider provider;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SkyLyrics-Lyrics"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private volatile long revision;
    private volatile Loaded loaded;
    private volatile boolean closed;

    public LyricsSession(Path root) {
        this.root = root;
        cache = new LyricsCache(root);
        provider = new LrclibProvider(cache, () -> SkyLyricsConfig.LRCLIB_IDENTITY.get());
    }

    public Loaded loaded() { return loaded; }

    public void request(PlaybackSnapshot track, String localName, boolean importForTrack) {
        request(track, localName, importForTrack, false);
    }
    public void request(PlaybackSnapshot track, String localName, boolean importForTrack, boolean force) {
        long requestRevision = ++revision;
        loaded = null;
        if (pending != null) pending.cancel(true);
        pending = worker.submit(() -> {
            LyricsResult result;
            try {
                if (force) cache.invalidate(track);
                if (localName != null) {
                    Path folder = root.resolve("imports");
                    Files.createDirectories(folder);
                    Path file = folder.resolve(localName).normalize();
                    if (!file.getParent().equals(folder) || !localName.toLowerCase(java.util.Locale.ROOT).endsWith(".lrc")
                            || Files.size(file) > 2_000_000) throw new IllegalArgumentException("Invalid LRC file");
                    String lrc = Files.readString(file);
                    var cues = LrcParser.parse(lrc, track.durationMs());
                    result = (cues.isEmpty() ? LyricsResult.empty(LyricsResult.State.NO_SYNCED_LYRICS)
                            : new LyricsResult(LyricsResult.State.SYNCED, cues))
                            .withDiagnostics(LyricsDiagnostics.local(track, cues.isEmpty() ? "No valid LRC timestamps" : "LRC associated with original track metadata"));
                    if (importForTrack && result.state() == LyricsResult.State.SYNCED) {
                        Path target = cache.localFile(track);
                        Files.createDirectories(target.getParent());
                        Files.writeString(target, lrc);
                    }
                } else result = provider.find(track);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            catch (Exception e) { result = LyricsResult.empty(localName != null ? LyricsResult.State.LOCAL_FILE_ERROR : LyricsResult.State.MALFORMED_RESPONSE)
                    .withDiagnostics(LyricsDiagnostics.local(track, e.getClass().getSimpleName() + ": " + e.getMessage())); }
            if (SkyLyricsConfig.LYRICS_DIAGNOSTICS.get()) com.mojang.logging.LogUtils.getLogger().info(
                    "SkyLyrics lookup diagnostics: {}", new com.google.gson.Gson().toJson(result.diagnostics()));
            if (!closed && revision == requestRevision) loaded = new Loaded(requestRevision, track.trackKey(), result);
        });
    }

    @Override public void close() {
        closed = true; revision++;
        if (pending != null) pending.cancel(true);
        worker.shutdownNow(); loaded = null;
    }
}
