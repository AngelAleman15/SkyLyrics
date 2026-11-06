package dev.skylyrics.client;

import dev.skylyrics.core.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.util.List;
import java.util.function.BiFunction;

/** Client-thread coordinator. Worker results are tagged so stale tracks cannot appear. */
public final class PlaybackCoordinator implements AutoCloseable {
    private final MusicLyricsController controller = new MusicLyricsController();
    private SpotifyMediaService media;
    private LyricsSession lyrics;
    private PlaybackSnapshot current;
    private boolean automatic = true;
    private String localName, requestedKey = "";
    private long localStartNs, localDurationMs, acceptedRevision = -1;
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
    public String status() { return status; }

    public void tick(long nowNs, long presentationNs, BiFunction<LyricCue, Long, WorldPhrase> spawn) {
        if (current == null) { controller.suspend(presentationNs); return; }
        String key = current.trackKey();
        if (!key.equals(requestedKey)) {
            requestedKey = key;
            controller.replace(List.of(), presentationNs);
            acceptedRevision = -1;
            lyrics.request(current, localName, false);
        }
        LyricsSession.Loaded loaded = lyrics.loaded();
        if (loaded != null && loaded.key().equals(key)) {
            status = loaded.result().state().name();
            if (loaded.revision() != acceptedRevision) {
                acceptedRevision = loaded.revision();
                controller.replace(loaded.result().cues(), presentationNs);
            }
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
        controller.replace(List.of(), 0);
        current = null; requestedKey = ""; localName = null;
        automatic = false; acceptedRevision = -1;
        status = "STOPPED";
    }
}
