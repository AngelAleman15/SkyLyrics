package dev.skylyrics.rhythm;

/** Render-safe scalar smoothing. No audio processing, positions, orientations or phrase deadlines. */
public final class SubtleRhythmController {
    public record Visual(double scale, double brightness, double pulse) { }
    private long previousNs;
    private double smoothed;
    public Visual sample(RhythmState state, long nowNs, boolean playing, RhythmSettings settings) {
        double dt = previousNs == 0 ? 0 : Math.max(0, Math.min(1, (nowNs - previousNs) / 1_000_000_000.0));
        previousNs = nowNs;
        double target = playing && Double.isFinite(state.pulse()) && state.analyzedNs() != 0
                && nowNs >= state.analyzedNs() && nowNs - state.analyzedNs() < 500_000_000L
                && nowNs >= state.capturedNs() && nowNs - state.capturedNs() < 500_000_000L
                ? Math.max(0, Math.min(1, state.pulse())) : 0;
        double tau = (target > smoothed ? settings.attackMs() : settings.releaseMs()) / 1000.0;
        smoothed += (target - smoothed) * (1 - Math.exp(-dt / tau));
        smoothed = Math.max(0, Math.min(1, smoothed));
        return new Visual(1 + settings.maxScalePulse() * smoothed, 1 + settings.maxBrightnessBoost() * smoothed, smoothed);
    }
    public static int brighten(int rgb, double factor) {
        double mix = Math.max(0, Math.min(.08, factor - 1));
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int)Math.round(r + (255 - r) * mix) << 16) | ((int)Math.round(g + (255 - g) * mix) << 8)
                | (int)Math.round(b + (255 - b) * mix);
    }
}
