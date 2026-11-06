package dev.skylyrics.client;

import dev.skylyrics.core.PlaybackSnapshot;
import dev.skylyrics.rhythm.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** Lifecycle on client tick, analysis on the audio worker, only a small state read during drawing. */
public final class SubtleRhythmEngine implements AutoCloseable {
    private AudioCaptureService capture;
    private final SubtleRhythmController controller = new SubtleRhythmController();
    private RhythmSettings settings = RhythmSettings.defaults();
    private String trackKey = "";
    private boolean playing;
    private long lastRenderAgeMs = -1;
    public void update(PlaybackSnapshot track, boolean spotifyPlaying) {
        settings = RhythmConfig.settings();
        playing = RhythmConfig.ENABLED.get() && SkyLyricsConfig.ENABLED.get() && spotifyPlaying;
        if (!playing) { close(); return; }
        if (capture == null) {
            capture = new AudioCaptureService(FMLPaths.CONFIGDIR.get().resolve("skylyrics/audio/windows"), settings);
            capture.start();
        }
        capture.settings(settings);
        if (!track.trackKey().equals(trackKey)) { trackKey = track.trackKey(); capture.reset(); }
    }
    public SubtleRhythmController.Visual visual(long nowNs) {
        var state = state();
        lastRenderAgeMs = state.capturedNs() == 0 ? -1 : Math.max(0, (nowNs - state.capturedNs()) / 1_000_000);
        return controller.sample(state, nowNs, playing, settings);
    }
    public long lastRenderAgeMs() { return lastRenderAgeMs; }
    public RhythmState state() { return capture == null ? RhythmState.silent() : capture.state(); }
    public String status() {
        if (!RhythmConfig.ENABLED.get()) return "DISABLED";
        if (!SkyLyricsConfig.ENABLED.get()) return "DISABLED_WITH_LYRICS";
        return capture != null ? capture.status() : "WAITING_FOR_SPOTIFY";
    }
    @Override public void close() { playing = false; if (capture != null) { capture.close(); capture = null; } }
}
