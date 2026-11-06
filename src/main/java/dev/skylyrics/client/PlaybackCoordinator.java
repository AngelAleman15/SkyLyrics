package dev.skylyrics.client;

import dev.skylyrics.core.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.util.List;
import java.util.function.BiFunction;
import dev.skylyrics.lyrics.*;

/** Client-thread coordinator. Worker results are tagged so stale tracks cannot appear. */
public final class PlaybackCoordinator implements AutoCloseable {
    private final MusicLyricsController controller = new MusicLyricsController();
    private SpotifyMediaService media;
    private LyricsSession lyrics;
    private PlaybackSnapshot current;
    private boolean automatic = true;
    private String localName, requestedKey = "";
    private long localStartNs, localDurationMs, acceptedRevision = -1;
    private long retryAtNs;
    private int automaticRetries;
    private String status = "IDLE";

    public void sample(long nowNs) {
        if (!automatic && localName == null) { current = null; return; }
        if (lyrics == null) lyrics = new LyricsSession(FMLPaths.CONFIGDIR.get().resolve("skylyrics"));
        if (localName != null) {
            long position = Math.max(0, (nowNs - localStartNs) / 1_000_000);
            current = position >= localDurationMs ? null : new PlaybackSnapshot(localName, "Local LRC", "",
                    localDurationMs, position, true, 1, nowNs);
            if (current == null) status = "LOCAL_ENDED";
        } else if (SkyLyricsConfig.SPOTIFY.get()) {
            if (media == null) { media = new SpotifyMediaService(); media.start(); }
            current = media.snapshot();
            status = media.status();
            if (current != null && nowNs - current.receivedNs() > 2_000_000_000L) {
                current = null; status = "STALE_MEDIA";
            }
        } else {
            if (media != null) { media.close(); media = null; }
            current = null; status = "SPOTIFY_DISABLED";
        }
    }

    public boolean paused() { return current != null && !current.playing(); }
    public PlaybackSnapshot current() { return current; }
    public boolean spotifyPlaying() { return automatic && localName == null && current != null && current.playing(); }
    public String status() { return status; }
    public LyricsResult result() { return lyrics == null || lyrics.loaded() == null ? null : lyrics.loaded().result(); }
    public boolean retry(long presentationNs) {
        if (current == null || lyrics == null) return false;
        controller.replace(List.of(), presentationNs);
        lyrics.request(current, localName, false, true);
        acceptedRevision = -1; status = "LYRICS_LOADING";
        return true;
    }

    public void tick(long nowNs, long presentationNs, BiFunction<LyricCue, Long, WorldPhrase> spawn,
                     Runnable onTrackChanged) {
        if (current == null) { controller.suspend(presentationNs); return; }
        String key = current.trackKey();
        if (!key.equals(requestedKey)) {
            onTrackChanged.run();
            requestedKey = key;
            controller.replace(List.of(), presentationNs);
            acceptedRevision = -1;
            automaticRetries = 0; retryAtNs = 0;
            lyrics.request(current, localName, false);
        }
        LyricsSession.Loaded loaded = lyrics.loaded();
        if (loaded == null) status = "LYRICS_LOADING";
        if (loaded != null && loaded.key().equals(key)) {
            status = loaded.result().state().name();
            if (loaded.revision() != acceptedRevision) {
                acceptedRevision = loaded.revision();
                controller.replace(loaded.result().cues(), presentationNs);
                var state = loaded.result().state();
                boolean retryable = state == LyricsResult.State.NETWORK_ERROR || state == LyricsResult.State.RATE_LIMITED
                        || (state == LyricsResult.State.HTTP_ERROR && loaded.result().retryAfterMs() > 0);
                retryAtNs = retryable && automaticRetries < 2 ? nowNs + Math.max(20_000L << automaticRetries,
                        loaded.result().retryAfterMs()) * 1_000_000L : 0;
            }
        }
        if (localName == null && retryAtNs != 0 && nowNs >= retryAtNs) {
            retryAtNs = 0; automaticRetries++;
            lyrics.request(current, null, false); status = "LYRICS_RETRYING";
        }
        controller.tick(current.positionAt(nowNs) + SkyLyricsConfig.SYNC_OFFSET_MS.get(),
                presentationNs, current.rate(), SkyLyricsConfig.ENABLED.get(), spawn);
    }

    public void playLocal(String name, long durationMs, long nowNs) {
        close();
        automatic = false; localName = name;
        localStartNs = nowNs; localDurationMs = durationMs;
        status = "LOCAL_LOADING";
    }

    public boolean importCurrent(String name, long presentationNs) {
        if (current == null || lyrics == null || current.durationMs() <= 0 || localName != null) return false;
        controller.replace(List.of(), presentationNs);
        lyrics.request(current, name, true);
        acceptedRevision = -1;
        return true;
    }

    public void spotify() { close(); automatic = true; status = "STARTING"; }

    @Override public void close() {
        if (media != null) { media.close(); media = null; }
        if (lyrics != null) { lyrics.close(); lyrics = null; }
        controller.clear();
        current = null; requestedKey = ""; localName = null;
        automatic = false; acceptedRevision = -1;
        retryAtNs = 0; automaticRetries = 0;
        status = "STOPPED";
    }
}
