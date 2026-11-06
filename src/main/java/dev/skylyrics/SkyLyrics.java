package dev.skylyrics;

import dev.skylyrics.client.SkyLyricsConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(SkyLyrics.MOD_ID)
public final class SkyLyrics {
    public static final String MOD_ID = "skylyrics";

    public SkyLyrics() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SkyLyricsConfig.SPEC);
    }
}
