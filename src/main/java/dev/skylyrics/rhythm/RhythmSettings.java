package dev.skylyrics.rhythm;

public record RhythmSettings(double sensitivity, double maxScalePulse, double maxBrightnessBoost,
                             int attackMs, int releaseMs, int minPulseIntervalMs,
                             boolean bassReaction, boolean transientReaction) {
    public static RhythmSettings defaults() { return new RhythmSettings(1, .02, .08, 80, 300, 180, true, true); }
    public RhythmSettings {
        if (!Double.isFinite(sensitivity) || sensitivity <= 0 || !Double.isFinite(maxScalePulse) || maxScalePulse < 0 || maxScalePulse > .02
                || !Double.isFinite(maxBrightnessBoost) || maxBrightnessBoost < 0 || maxBrightnessBoost > .08 || attackMs < 1 || releaseMs < 1 || minPulseIntervalMs < 100)
            throw new IllegalArgumentException("Invalid Subtle Rhythm settings");
    }
}
