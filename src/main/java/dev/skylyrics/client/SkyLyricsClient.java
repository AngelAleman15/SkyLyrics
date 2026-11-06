package dev.skylyrics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.skylyrics.SkyLyrics;
import dev.skylyrics.core.LyricCue;
import dev.skylyrics.core.LyricsScene;
import dev.skylyrics.core.LyricsSynchronizer;
import dev.skylyrics.core.WorldAnchor;
import dev.skylyrics.core.WorldPhrase;
import dev.skylyrics.core.PresentationClock;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = SkyLyrics.MOD_ID, value = Dist.CLIENT)
public final class SkyLyricsClient {
    private static final LyricsScene SCENE = new LyricsScene();
    private static final LyricsPlacementManager PLACEMENT = new LyricsPlacementManager();
    private static final LyricsWorldRenderer RENDERER = new LyricsWorldRenderer();
    private static final PlaybackCoordinator PLAYBACK = new PlaybackCoordinator();
    private static final PresentationClock CLOCK = new PresentationClock(System.nanoTime());
    private static final KeyMapping TOGGLE = new KeyMapping("key.skylyrics.toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.skylyrics");
    private static final LyricsSynchronizer DEMO = new LyricsSynchronizer(List.of(
            new LyricCue(0, 6000, "SkyLyrics · DEMO"),
            new LyricCue(6000, 12000, "Estas palabras se quedan aquí"),
            new LyricCue(12000, 18000, "Podés caminar debajo de ellas"),
            new LyricCue(18000, 24000, "El paisaje guarda su lugar"),
            new LyricCue(24000, 30000, "Girá: el cielo sigue ahí"),
            new LyricCue(30000, 36000, "La frase se desvanece donde nació")));
    private static Object level;

    private SkyLyricsClient() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Clear only when the world is actually replaced, never when the player moves.
        if (level != mc.level) {
            reset();
            level = mc.level;
            if (mc.level != null) PLAYBACK.spotify();
        }
        while (TOGGLE.consumeClick()) {
            SkyLyricsConfig.ENABLED.set(!SkyLyricsConfig.ENABLED.get());
            SkyLyricsConfig.SPEC.save();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(
                    "SkyLyrics: " + (SkyLyricsConfig.ENABLED.get() ? "activado" : "desactivado")), true);
        }
        long now = System.nanoTime();
        if (mc.player == null || mc.level == null) return;
        PLAYBACK.sample(now);
        CLOCK.update(now, !DEMO.running() && PLAYBACK.paused());
        long presentation = CLOCK.at(now);
        DEMO.tick(now, SkyLyricsConfig.ENABLED.get(), cue -> spawn(cue.text(), presentation, cue.endMs() - cue.startMs()));
        PLAYBACK.tick(now, presentation, (cue, duration) -> spawn(cue.text(), presentation, duration),
                () -> SCENE.retireAll(presentation, now, SkyLyricsConfig.TRACK_CHANGE_FADE_MS.get()));
        SCENE.expire(presentation, now);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !SkyLyricsConfig.ENABLED.get() || SCENE.phrases().isEmpty()
                || Minecraft.getInstance().level == null) return;
        long now = System.nanoTime();
        RENDERER.render(event, SCENE.phrases(), CLOCK.at(now), now);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        level = null;
    }

    private static void reset() {
        DEMO.stop();
        SCENE.clear();
        LyricsTypography.clear();
        PLAYBACK.close();
    }

    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) { reset(); }

    private static WorldPhrase spawn(String text, long nowNs, long durationMs) {
        Minecraft mc = Minecraft.getInstance();
        WorldAnchor anchor = PLACEMENT.create(mc, text, SCENE.phrases());
        if (anchor == null || !SCENE.add(text, anchor, nowNs, durationMs,
                SkyLyricsConfig.FADE_IN_MS.get(), SkyLyricsConfig.FADE_OUT_MS.get(),
                SkyLyricsConfig.MAX_PHRASES.get(), System.nanoTime())) return null;
        return SCENE.phrases().get(SCENE.phrases().size() - 1);
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("skylyrics")
                .then(literal("demo").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null || mc.player == null) return 0;
                    reset();
                    level = mc.level;
                    DEMO.start(System.nanoTime());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "SkyLyrics: DEMO de 36 s, sin conexión a Spotify. /skylyrics stop para terminar."), false);
                    return 1;
                }))
                .then(literal("stop").executes(ctx -> { reset(); return 1; }))
                .then(literal("anchor").then(argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null || mc.player == null) return 0;
                    DEMO.stop();
                    PLAYBACK.close();
                    CLOCK.update(System.nanoTime(), false);
                    String text = StringArgumentType.getString(ctx, "text");
                    WorldAnchor anchor = PLACEMENT.create(mc, text, SCENE.phrases());
                    if (anchor == null) return 0;
                    // Manual 30-second test phrase. Explicit creation, never a visibility repair.
                    return SCENE.add(text, anchor,
                            CLOCK.at(System.nanoTime()), 30_000, SkyLyricsConfig.FADE_IN_MS.get(),
                            SkyLyricsConfig.FADE_OUT_MS.get(), SkyLyricsConfig.MAX_PHRASES.get(), System.nanoTime()) ? 1 : 0;
                })))
                .then(literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("SkyLyrics | " + PLAYBACK.status() + " | demo="
                            + DEMO.running() + " | frases=" + SCENE.phrases().size()), false);
                    for (var phrase : SCENE.phrases()) {
                        var a = phrase.anchor();
                        String report = String.format(Locale.ROOT, "#%d (%.3f, %.3f, %.3f) yaw=%.1f pitch=%.1f roll=%.1f",
                                phrase.id(), a.x(), a.y(), a.z(), a.yawDegrees(), a.pitchDegrees(), a.rollDegrees());
                        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
                    }
                    return 1;
                }))
                .then(literal("spotify").executes(ctx -> { reset(); PLAYBACK.spotify(); return 1; }))
                .then(literal("config").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.tell(() -> mc.setScreen(new SkyLyricsConfigScreen(null)));
                    return 1;
                }))
                .then(literal("import").then(argument("filename", StringArgumentType.string()).executes(ctx -> {
                    boolean accepted = PLAYBACK.importCurrent(StringArgumentType.getString(ctx, "filename"), CLOCK.at(System.nanoTime()));
                    ctx.getSource().sendSuccess(() -> Component.literal(accepted
                            ? "Leyendo LRC local para la canción de Spotify actual."
                            : "No hay una canción de Spotify con duración disponible. Usá /skylyrics lrc para una prueba manual."), false);
                    return accepted ? 1 : 0;
                })))
                .then(literal("lrc").then(argument("filename", StringArgumentType.string())
                        .then(argument("durationSeconds", IntegerArgumentType.integer(1, 3600)).executes(ctx -> {
                            reset();
                            PLAYBACK.playLocal(StringArgumentType.getString(ctx, "filename"),
                                    IntegerArgumentType.getInteger(ctx, "durationSeconds") * 1000L, System.nanoTime());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "LRC local: reloj manual iniciado; no reproduce audio ni detecta Spotify."), false);
                            return 1;
                        })))));
    }

    @Mod.EventBusSubscriber(modid = SkyLyrics.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); }
        @SubscribeEvent
        public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> LyricsTypography.clear());
        }
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SkyLyricsConfigScreen(parent)));
        }
    }
}
