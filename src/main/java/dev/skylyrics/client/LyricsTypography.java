package dev.skylyrics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded layout cache, shared by birth-time bounds and drawing; no font/network I/O here. */
public final class LyricsTypography {
    private static final ResourceLocation EDITORIAL = new ResourceLocation("skylyrics", "editorial");
    private static final ResourceLocation VANILLA = new ResourceLocation("minecraft", "default");
    public record Line(FormattedCharSequence text, int width) { }
    public record Layout(List<Line> lines, int width, int height, int lineSpacing) { }
    private static final Map<String, Layout> CACHE = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Layout> entry) { return size() > 32; }
    };
    private static SkyLyricsConfig.FontStyle cachedStyle;
    private LyricsTypography() { }

    public static void clear() { CACHE.clear(); }

    public static Layout layout(Minecraft mc, String text) {
        var style = SkyLyricsConfig.FONT_STYLE.get();
        if (style != cachedStyle) { clear(); cachedStyle = style; }
        Layout cached = CACHE.get(text);
        if (cached != null) return cached;
        ResourceLocation font = style == SkyLyricsConfig.FontStyle.CUSTOM ? EDITORIAL : VANILLA;
        var component = Component.literal(text).withStyle(s -> s.withFont(font));
        var wrapped = mc.font.split(component, 180);
        List<Line> lines = new ArrayList<>();
        int width = 1;
        for (int i = 0; i < Math.min(3, wrapped.size()); i++) {
            FormattedCharSequence sequence = wrapped.get(i);
            if (i == 2 && wrapped.size() > 3) {
                // Indicate truncation, never draw a paragraph covering the entire landscape.
                sequence = FormattedCharSequence.composite(sequence,
                        Component.literal("…").withStyle(s -> s.withFont(font)).getVisualOrderText());
            }
            int lineWidth = mc.font.width(sequence);
            width = Math.max(width, lineWidth);
            lines.add(new Line(sequence, lineWidth));
        }
        int spacing = style == SkyLyricsConfig.FontStyle.CUSTOM ? 13 : 11;
        Layout layout = new Layout(List.copyOf(lines), width, Math.max(1, lines.size()) * spacing, spacing);
        CACHE.put(text, layout);
        return layout;
    }
}
