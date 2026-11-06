package dev.skylyrics.client;

import dev.skylyrics.rhythm.RhythmSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/** Defined inside SkyLyrics' existing persistent client configuration. SYSTEM is the only implemented source. */
public final class RhythmConfig {
    public enum AudioCaptureMode { SYSTEM }
    public static ForgeConfigSpec.BooleanValue ENABLED, BASS, TRANSIENTS;
    public static ForgeConfigSpec.EnumValue<AudioCaptureMode> MODE;
    public static ForgeConfigSpec.DoubleValue SENSITIVITY, MAX_SCALE, MAX_BRIGHTNESS;
    public static ForgeConfigSpec.IntValue ATTACK, RELEASE, MIN_INTERVAL;
    private RhythmConfig() { }
    public static void define(ForgeConfigSpec.Builder b) {
        ENABLED = b.comment("Explicit opt-in. Captures SYSTEM OUTPUT MIX, including game/Discord/other apps. No audio is saved or sent to a server.")
                .define("enableSubtleRhythm", false);
        MODE = b.defineEnum("audioCaptureMode", AudioCaptureMode.SYSTEM);
        SENSITIVITY = b.defineInRange("rhythmSensitivity", 1.0, .1, 3.0);
        MAX_SCALE = b.defineInRange("maxScalePulse", .02, 0.0, .02);
        MAX_BRIGHTNESS = b.defineInRange("maxBrightnessBoost", .08, 0.0, .08);
        ATTACK = b.defineInRange("attackMs", 80, 1, 1000);
        RELEASE = b.defineInRange("releaseMs", 300, 20, 3000);
        MIN_INTERVAL = b.defineInRange("minPulseIntervalMs", 180, 100, 2000);
        BASS = b.define("enableBassReaction", true);
        TRANSIENTS = b.define("enableTransientReaction", true);
    }
    public static RhythmSettings settings() { return new RhythmSettings(SENSITIVITY.get(), MAX_SCALE.get(), MAX_BRIGHTNESS.get(),
            ATTACK.get(), RELEASE.get(), MIN_INTERVAL.get(), BASS.get(), TRANSIENTS.get()); }
}
