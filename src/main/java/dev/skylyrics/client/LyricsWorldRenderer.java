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

    public void render(RenderLevelStageEvent event, Iterable<WorldPhrase> phrases, long nowNs) {
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
                int alpha = Math.round(AnimationController.alpha(phrase, nowNs)
                        * SkyLyricsConfig.OPACITY.get().floatValue() * 255);
                // Vanilla Font treats very low alpha as opaque. Skip instead of flashing.
                if (alpha < 4) continue;
                WorldAnchor anchor = phrase.anchor();
                pose.pushPose();
                try {
                    // View transform only. The stored absolute anchor NEVER changes.
                    pose.translate(anchor.x() - camera.x, anchor.y() - camera.y, anchor.z() - camera.z);
                    if (SkyLyricsConfig.BILLBOARD.get()) {
                        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                    } else {
                        pose.mulPose(Axis.YP.rotationDegrees(-anchor.yawDegrees()));
                    }
                    pose.scale(-anchor.scale(), -anchor.scale(), anchor.scale());
                    mc.font.drawInBatch(phrase.text(), -mc.font.width(phrase.text()) / 2f,
                            -mc.font.lineHeight / 2f, (alpha << 24) | SkyLyricsConfig.COLOR.get(),
                            false, pose.last().pose(), buffers, mode, 0, LightTexture.FULL_BRIGHT);
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
