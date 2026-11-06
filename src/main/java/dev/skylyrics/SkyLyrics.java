package dev.skylyrics;

import dev.skylyrics.client.SkyLyricsConfig;
import dev.skylyrics.core.ConfigMigration;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(SkyLyrics.MOD_ID)
public final class SkyLyrics {
    public static final String MOD_ID = "skylyrics";

    public SkyLyrics() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try { ConfigMigration.migrate(FMLPaths.CONFIGDIR.get().resolve("skylyrics-client.toml")); }
            catch (java.io.IOException e) { LogUtils.getLogger().warn("SkyLyrics: could not migrate legacy configuration", e); }
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SkyLyricsConfig.SPEC);
    }
}
