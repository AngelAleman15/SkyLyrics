package dev.skylyrics.client;

import net.minecraftforge.common.ForgeConfigSpec;
import dev.skylyrics.core.PlacementDistribution;

public final class SkyLyricsConfig {
    public enum Visibility { NATURAL, ETHEREAL, SKY }
    public enum Style { CINEMATIC, AMBIENT }
    public enum FontStyle { CUSTOM, VANILLA }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, BILLBOARD, SPOTIFY, LYRICS_DIAGNOSTICS;
    public static final ForgeConfigSpec.ConfigValue<String> LRCLIB_IDENTITY;
    public static final ForgeConfigSpec.IntValue SYNC_OFFSET_MS;
    public static final ForgeConfigSpec.EnumValue<Visibility> VISIBILITY;
    public static final ForgeConfigSpec.EnumValue<Style> STYLE;
    public static final ForgeConfigSpec.EnumValue<FontStyle> FONT_STYLE;
    public static final ForgeConfigSpec.EnumValue<PlacementDistribution.Mode> PLACEMENT_MODE;
    public static final ForgeConfigSpec.DoubleValue SIDE_BIAS, HEIGHT_VARIATION, ROTATION_VARIATION;
    public static final ForgeConfigSpec.IntValue LEFT_WEIGHT, RIGHT_WEIGHT, ELEVATED_WEIGHT, FRONT_WEIGHT, TRACK_CHANGE_FADE_MS;
    public static final ForgeConfigSpec.DoubleValue MIN_DISTANCE, MAX_DISTANCE,
            MIN_HEIGHT, MAX_HEIGHT, TEXT_SCALE, OPACITY;
    public static final ForgeConfigSpec.IntValue FADE_IN_MS, FADE_OUT_MS, MAX_PHRASES, COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.define("enabled", true);
        SPOTIFY = b.define("spotify", true);
        LYRICS_DIAGNOSTICS = b.comment("Opt-in metadata/query/rejection diagnostics in the local game log. Never logs lyric text.")
                .define("lyricsDiagnostics", false);
        LRCLIB_IDENTITY = b.comment("Real public project URL or contact required by LRCLIB; empty disables uncached HTTP lookups.")
                .define("lrclibIdentity", "https://github.com/AngelAleman15/SkyLyrics");
        SYNC_OFFSET_MS = b.comment("Positive offset displays lyrics earlier. Does not affect existing anchors.")
                .defineInRange("syncOffsetMs", 0, -10000, 10000);
        BILLBOARD = b.comment("Rotation only. Anchors never move.").define("enableBillboarding", true);
        FONT_STYLE = b.defineEnum("fontStyle", FontStyle.CUSTOM);
        PLACEMENT_MODE = b.defineEnum("placementMode", PlacementDistribution.Mode.CINEMATIC);
        SIDE_BIAS = b.comment("Multiplier for left/right weights; 1 preserves the configured 40/40/10/10 distribution.")
                .defineInRange("sideBias", 1.0, 0.0, 4.0);
        LEFT_WEIGHT = b.defineInRange("leftWeight", 40, 0, 100);
        RIGHT_WEIGHT = b.defineInRange("rightWeight", 40, 0, 100);
        ELEVATED_WEIGHT = b.defineInRange("elevatedWeight", 10, 0, 100);
        FRONT_WEIGHT = b.defineInRange("frontWeight", 10, 0, 100);
        HEIGHT_VARIATION = b.comment("Fraction of the minHeight/maxHeight range sampled around its midpoint.")
                .defineInRange("heightVariation", 0.8, 0.0, 1.0);
        ROTATION_VARIATION = b.comment("Maximum static roll in degrees; smaller pitch/yaw variations are captured at birth too.")
                .defineInRange("rotationVariation", 5.0, 0.0, 12.0);
        VISIBILITY = b.defineEnum("visibility", Visibility.SKY);
        STYLE = b.defineEnum("style", Style.CINEMATIC);
        MIN_DISTANCE = b.defineInRange("minDistance", 12.0, 6.0, 128.0);
        MAX_DISTANCE = b.defineInRange("maxDistance", 45.0, 6.0, 128.0);
        MIN_HEIGHT = b.defineInRange("minHeight", 5.0, 0.0, 128.0);
        MAX_HEIGHT = b.defineInRange("maxHeight", 25.0, 0.0, 128.0);
        TEXT_SCALE = b.comment("Base blocks per font pixel at creation; never scales with camera motion.")
                .defineInRange("textScale", 0.14, 0.005, 0.5);
        OPACITY = b.defineInRange("textOpacity", 0.92, 0.0, 1.0);
        COLOR = b.comment("RGB integer. Default warm white #FFF1DC.")
                .defineInRange("color", 0xFFF1DC, 0, 0xFFFFFF);
        FADE_IN_MS = b.comment("Musical milliseconds, including the 95% to 100% scale entrance.")
                .defineInRange("fadeInDuration", 700, 0, 5000);
        FADE_OUT_MS = b.defineInRange("fadeOutDuration", 1000, 0, 5000);
        TRACK_CHANGE_FADE_MS = b.comment("Real-time milliseconds; keeps finishing even when the next track is paused.")
                .defineInRange("trackChangeFadeMs", 350, 0, 2000);
        MAX_PHRASES = b.defineInRange("maxVisibleLyrics", 4, 1, 16);
        RhythmConfig.define(b);
        SPEC = b.build();
    }

    private SkyLyricsConfig() { }
}
