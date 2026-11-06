package dev.skylyrics.client;

import dev.skylyrics.core.PlacementDistribution;
import dev.skylyrics.core.PlacementGeometry;
import dev.skylyrics.core.WorldAnchor;
import dev.skylyrics.core.WorldPhrase;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Bounded candidate search at birth only. Existing appearances are read-only obstacles. */
public final class LyricsPlacementManager {
    public WorldAnchor create(Minecraft mc, String text, List<WorldPhrase> existing) {
        if (mc.player == null || mc.level == null) throw new IllegalStateException("No world");
        Vec3 eye = mc.player.getEyePosition();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        var mode = SkyLyricsConfig.PLACEMENT_MODE.get();
        var zone = PlacementDistribution.choose(mode, SkyLyricsConfig.SIDE_BIAS.get(),
                SkyLyricsConfig.LEFT_WEIGHT.get(), SkyLyricsConfig.RIGHT_WEIGHT.get(),
                SkyLyricsConfig.ELEVATED_WEIGHT.get(), SkyLyricsConfig.FRONT_WEIGHT.get(), random);
        var layout = LyricsTypography.layout(mc, text);
        double near = Math.max(6, Math.min(SkyLyricsConfig.MIN_DISTANCE.get(), SkyLyricsConfig.MAX_DISTANCE.get()));
        double far = Math.max(near, Math.max(SkyLyricsConfig.MIN_DISTANCE.get(), SkyLyricsConfig.MAX_DISTANCE.get()));
        double low = Math.min(SkyLyricsConfig.MIN_HEIGHT.get(), SkyLyricsConfig.MAX_HEIGHT.get());
        double high = Math.max(SkyLyricsConfig.MIN_HEIGHT.get(), SkyLyricsConfig.MAX_HEIGHT.get());
        double span = (high - low) * SkyLyricsConfig.HEIGHT_VARIATION.get();
        double center = (high + low) / 2;
        double rotation = SkyLyricsConfig.ROTATION_VARIATION.get();
        double targetDistance = PlacementDistribution.between(near, far, random);
        double targetHeight = PlacementDistribution.between(center - span / 2, center + span / 2, random);
        float style = SkyLyricsConfig.STYLE.get() == SkyLyricsConfig.Style.AMBIENT ? 0.60f : 1f;
        boolean ethereal = SkyLyricsConfig.VISIBILITY.get() == SkyLyricsConfig.Visibility.ETHEREAL;
        boolean sky = SkyLyricsConfig.VISIBILITY.get() == SkyLyricsConfig.Visibility.SKY;
        WorldAnchor selected = null;
        double bestScore = -Double.MAX_VALUE;
        for (int attempt = 0; attempt < 16; attempt++) {
            double distance = PlacementDistribution.between(near, far, random);
            double offset = PlacementDistribution.yawOffset(zone, mode, random);
            double yaw = Math.toRadians(mc.player.getYRot() + offset);
            double height = PlacementDistribution.between(center - span / 2, center + span / 2, random);
            if (zone == PlacementDistribution.Zone.ELEVATED) height = Math.max(height, low + (high - low) * .85);
            else if (sky) height = Math.max(height, low + (high - low) * .35);
            double x = eye.x - Math.sin(yaw) * distance;
            double z = eye.z + Math.cos(yaw) * distance;
            BlockPos column = BlockPos.containing(x, eye.y, z);
            if (!mc.level.hasChunkAt(column)) continue;
            double surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            double y = Math.max(eye.y + height, surface + 3);
            if (y > mc.level.getMaxBuildHeight() + 32) continue;
            Vec3 candidate = new Vec3(x, y, z);
            if (!loadedSegment(mc, eye, candidate)) continue;
            float scale = (float) (SkyLyricsConfig.TEXT_SCALE.get() * style * distance / 24.0);
            // Cap full-size extents at birth. Walking closer never recomputes this scale.
            scale = Math.min(scale, Math.min(12f / layout.width(), 3.5f / layout.height()));
            double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
            float facingYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float facingPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            WorldAnchor anchor = new WorldAnchor(x, y, z, scale,
                    facingYaw + (float) PlacementDistribution.between(-rotation * .3, rotation * .3, random),
                    facingPitch + (float) PlacementDistribution.between(-rotation * .3, rotation * .3, random),
                    (float) PlacementDistribution.between(-rotation, rotation, random),
                    Math.max(.01f, layout.width() * scale), Math.max(.01f, layout.height() * scale));
            boolean overlap = false;
            for (WorldPhrase previous : existing) {
                if (PlacementGeometry.overlaps(anchor, previous.anchor(), eye.x, eye.y, eye.z)) {
                    overlap = true; break;
                }
            }
            if (overlap) continue;
            boolean visible = ethereal;
            if (!ethereal) {
                HitResult hit = mc.level.clip(new ClipContext(eye, candidate,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                visible = hit.getType() == HitResult.Type.MISS;
            }
            // Score within the chosen zone; no reward for moving a lateral cue to the center.
            double viewPitch = -mc.player.getXRot();
            double pitchDifference = Math.abs(Math.toDegrees(Math.atan2(dy, distance)) - viewPitch);
            double score = (visible ? 4 : 0)
                    - Math.abs(distance - targetDistance) / Math.max(1, far - near) * .8
                    - Math.abs(height - targetHeight) / Math.max(1, span) * .6
                    - Math.max(0, pitchDifference - 35) / 90 * .2 + random.nextDouble() * .15;
            if (score > bestScore) { selected = anchor; bestScore = score; }
        }
        return selected; // Null means skip this cue, never retry it to keep it visible.
    }

    private boolean loadedSegment(Minecraft mc, Vec3 from, Vec3 to) {
        int steps = (int) Math.ceil(from.distanceTo(to) / 4);
        for (int i = 0; i <= steps; i++) {
            if (!mc.level.hasChunkAt(BlockPos.containing(from.lerp(to, i / (double) Math.max(1, steps))))) return false;
        }
        return true;
    }
}
