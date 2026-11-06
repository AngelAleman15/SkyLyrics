package dev.skylyrics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.skylyrics.core.AnimationController;
import dev.skylyrics.core.WorldAnchor;
import dev.skylyrics.core.WorldPhrase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/** Draw-only consumer: cannot modify, replace or relocate a phrase. */
public final class LyricsWorldRenderer {
    // Dedicated reusable buffer so flushing lyrics does not flush other mods' buffers.
    private final MultiBufferSource.BufferSource buffers =
            MultiBufferSource.immediate(new BufferBuilder(8192));

    public void render(RenderLevelStageEvent event, Iterable<WorldPhrase> phrases, long musicalNs, long realNs) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        Font.DisplayMode mode = SkyLyricsConfig.VISIBILITY.get() == SkyLyricsConfig.Visibility.ETHEREAL
                ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // See-through RenderType assumes depth is already disabled at its call site.
        if (mode == Font.DisplayMode.SEE_THROUGH) RenderSystem.disableDepthTest();
        try {
            for (WorldPhrase phrase : phrases) {
                int alpha = Math.round(AnimationController.alpha(phrase, musicalNs, realNs)
                        * SkyLyricsConfig.OPACITY.get().floatValue() * 255);
                // Vanilla Font treats very low alpha as opaque. Skip instead of flashing.
                if (alpha < 4) continue;
                WorldAnchor anchor = phrase.anchor();
                double dx = anchor.x() - camera.x, dy = anchor.y() - camera.y, dz = anchor.z() - camera.z;
                // Near-plane clipping only: never move a phrase away from the camera.
                if (dx * dx + dy * dy + dz * dz < .75 * .75) continue;
                var layout = LyricsTypography.layout(mc, phrase.text());
                pose.pushPose();
                try {
                    // View transform only. The stored absolute anchor NEVER changes.
                    pose.translate(dx, dy, dz);
                    if (SkyLyricsConfig.BILLBOARD.get()) {
                        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                    } else {
                        pose.mulPose(Axis.YP.rotationDegrees(180 - anchor.yawDegrees()));
                        pose.mulPose(Axis.XP.rotationDegrees(-anchor.pitchDegrees()));
                    }
                    pose.mulPose(Axis.ZP.rotationDegrees(anchor.rollDegrees()));
                    float scale = anchor.scale() * AnimationController.scale(phrase, musicalNs);
                    pose.scale(-scale, -scale, scale);
                    int shadowAlpha = Math.round(alpha * .18f);
                    for (int i = 0; i < layout.lines().size(); i++) {
                        var line = layout.lines().get(i);
                        float x = -line.width() / 2f, y = -layout.height() / 2f + i * layout.lineSpacing();
                        if (shadowAlpha >= 4) {
                            mc.font.drawInBatch(line.text(), x + .65f, y + .65f,
                                    (shadowAlpha << 24) | 0x362F29, false, pose.last().pose(),
                                    buffers, mode, 0, LightTexture.FULL_BRIGHT);
                        }
                        mc.font.drawInBatch(line.text(), x, y, (alpha << 24) | SkyLyricsConfig.COLOR.get(),
                                false, pose.last().pose(), buffers, mode, 0, LightTexture.FULL_BRIGHT);
                    }
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            try { buffers.endBatch(); }
            finally {
                if (previousDepthTest) RenderSystem.enableDepthTest();
                else RenderSystem.disableDepthTest();
                RenderSystem.depthMask(previousDepthWrite);
            }
        }
    }
}
