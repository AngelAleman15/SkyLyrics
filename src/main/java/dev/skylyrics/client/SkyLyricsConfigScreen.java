package dev.skylyrics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small vanilla screen; detailed ranges and LRCLIB identity remain in the TOML file. */
public final class SkyLyricsConfigScreen extends Screen {
    private final Screen parent;
    public SkyLyricsConfigScreen(Screen parent) { super(Component.literal("SkyLyrics")); this.parent = parent; }

    @Override protected void init() {
        int x = width / 2 - 150;
        int y = Math.max(32, height / 2 - 108);
        addRenderableWidget(Button.builder(label("Letras", SkyLyricsConfig.ENABLED.get()), button -> {
            SkyLyricsConfig.ENABLED.set(!SkyLyricsConfig.ENABLED.get());
            button.setMessage(label("Letras", SkyLyricsConfig.ENABLED.get())); save();
        }).bounds(x, y, 145, 20).build());
        addRenderableWidget(Button.builder(label("Spotify", SkyLyricsConfig.SPOTIFY.get()), button -> {
            SkyLyricsConfig.SPOTIFY.set(!SkyLyricsConfig.SPOTIFY.get());
            button.setMessage(label("Spotify", SkyLyricsConfig.SPOTIFY.get())); save();
        }).bounds(x + 155, y, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal(SkyLyricsConfig.VISIBILITY.get().name()), button -> {
            var values = SkyLyricsConfig.Visibility.values();
            SkyLyricsConfig.VISIBILITY.set(values[(SkyLyricsConfig.VISIBILITY.get().ordinal() + 1) % values.length]);
            button.setMessage(Component.literal(SkyLyricsConfig.VISIBILITY.get().name())); save();
        }).bounds(x, y + 26, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal(SkyLyricsConfig.STYLE.get().name()), button -> {
            var values = SkyLyricsConfig.Style.values();
            SkyLyricsConfig.STYLE.set(values[(SkyLyricsConfig.STYLE.get().ordinal() + 1) % values.length]);
            button.setMessage(Component.literal(SkyLyricsConfig.STYLE.get().name())); save();
        }).bounds(x + 155, y + 26, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fuente: " + SkyLyricsConfig.FONT_STYLE.get().name()), button -> {
            var values = SkyLyricsConfig.FontStyle.values();
            SkyLyricsConfig.FONT_STYLE.set(values[(SkyLyricsConfig.FONT_STYLE.get().ordinal() + 1) % values.length]);
            button.setMessage(Component.literal("Fuente: " + SkyLyricsConfig.FONT_STYLE.get().name())); save();
        }).bounds(x, y + 52, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal(SkyLyricsConfig.PLACEMENT_MODE.get().name()), button -> {
            var values = dev.skylyrics.core.PlacementDistribution.Mode.values();
            SkyLyricsConfig.PLACEMENT_MODE.set(values[(SkyLyricsConfig.PLACEMENT_MODE.get().ordinal() + 1) % values.length]);
            button.setMessage(Component.literal(SkyLyricsConfig.PLACEMENT_MODE.get().name())); save();
        }).bounds(x + 155, y + 52, 145, 20).build());
        addRenderableWidget(Button.builder(label("Billboard", SkyLyricsConfig.BILLBOARD.get()), button -> {
            SkyLyricsConfig.BILLBOARD.set(!SkyLyricsConfig.BILLBOARD.get());
            button.setMessage(label("Billboard", SkyLyricsConfig.BILLBOARD.get())); save();
        }).bounds(x, y + 78, 300, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Desfase −100 ms"), b -> offset(-100))
                .bounds(x, y + 104, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Desfase +100 ms"), b -> offset(100))
                .bounds(x + 155, y + 104, 145, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Listo"), b -> onClose())
                .bounds(x, y + 148, 300, 20).build());
    }

    private static Component label(String name, boolean on) { return Component.literal(name + ": " + (on ? "sí" : "no")); }
    private static void save() { SkyLyricsConfig.SPEC.save(); }
    private static void offset(int delta) {
        SkyLyricsConfig.SYNC_OFFSET_MS.set(Math.max(-10000, Math.min(10000, SkyLyricsConfig.SYNC_OFFSET_MS.get() + delta)));
        save();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFF1DC);
        graphics.drawCenteredString(font, "Desfase: " + SkyLyricsConfig.SYNC_OFFSET_MS.get() + " ms", width / 2,
                Math.max(32, height / 2 - 108) + 131, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
