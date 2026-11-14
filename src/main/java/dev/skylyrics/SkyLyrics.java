package dev.skylyrics;

import dev.skylyrics.client.SkyLyricsConfig;
import dev.skylyrics.client.SkyLyricsConfigScreen;
import dev.skylyrics.core.ConfigMigration;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.api.distmarker.Dist;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SkyLyrics.MOD_ID, dist = Dist.CLIENT)
public final class SkyLyrics {
    public static final String MOD_ID = "skylyrics";
    public SkyLyrics(ModContainer container) {
        try { ConfigMigration.migrate(FMLPaths.CONFIGDIR.get().resolve("skylyrics-client.toml")); }
        catch (java.io.IOException e) { LogUtils.getLogger().warn("SkyLyrics: could not migrate legacy configuration", e); }
        container.registerConfig(ModConfig.Type.CLIENT, SkyLyricsConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new SkyLyricsConfigScreen(parent));
    }
}
