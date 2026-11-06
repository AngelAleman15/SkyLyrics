package dev.skylyrics.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SkyLyricsConfig {
    public enum Visibility { NATURAL, ETHEREAL, SKY }
    public enum Style { CINEMATIC, AMBIENT }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, BILLBOARD, SPOTIFY;
    public static final ForgeConfigSpec.ConfigValue<String> LRCLIB_IDENTITY;
    public static final ForgeConfigSpec.IntValue SYNC_OFFSET_MS;
    public static final ForgeConfigSpec.EnumValue<Visibility> VISIBILITY;
    public static final ForgeConfigSpec.EnumValue<Style> STYLE;
    public static final ForgeConfigSpec.DoubleValue MIN_DISTANCE, MAX_DISTANCE,
            MIN_HEIGHT, MAX_HEIGHT, TEXT_SCALE, OPACITY;
    public static final ForgeConfigSpec.IntValue FADE_IN_MS, FADE_OUT_MS, MAX_PHRASES, COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.define("enabled", true);
        SPOTIFY = b.define("spotify", true);
        LRCLIB_IDENTITY = b.comment("Real public project URL or contact required by LRCLIB; empty disables uncached HTTP lookups.")
                .define("lrclibIdentity", "https://github.com/AngelAleman15/SkyLyrics");
        SYNC_OFFSET_MS = b.comment("Positive offset displays lyrics earlier. Does not affect existing anchors.")
                .defineInRange("syncOffsetMs", 0, -10000, 10000);
        BILLBOARD = b.comment("Rotation only. Anchors never move.").define("billboard", true);
        VISIBILITY = b.defineEnum("visibility", Visibility.SKY);
        STYLE = b.defineEnum("style", Style.CINEMATIC);
        MIN_DISTANCE = b.defineInRange("minDistance", 12.0, 1.0, 128.0);
        MAX_DISTANCE = b.defineInRange("maxDistance", 45.0, 1.0, 128.0);
        MIN_HEIGHT = b.defineInRange("minHeight", 5.0, 0.0, 128.0);
        MAX_HEIGHT = b.defineInRange("maxHeight", 25.0, 0.0, 128.0);
        TEXT_SCALE = b.comment("Base blocks per font pixel at creation; never scales with camera motion.")
                .defineInRange("textScale", 0.14, 0.005, 0.5);
        OPACITY = b.defineInRange("opacity", 0.92, 0.0, 1.0);
        COLOR = b.comment("RGB integer. Default warm white #FFF1DC.")
                .defineInRange("color", 0xFFF1DC, 0, 0xFFFFFF);
        FADE_IN_MS = b.defineInRange("fadeInMs", 550, 0, 5000);
        FADE_OUT_MS = b.defineInRange("fadeOutMs", 900, 0, 5000);
        MAX_PHRASES = b.defineInRange("maxPhrases", 4, 1, 16);
        SPEC = b.build();
    }

    private SkyLyricsConfig() { }
}
