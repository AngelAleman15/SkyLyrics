package dev.skylyrics.client;

import dev.skylyrics.core.WorldAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Called only when a new cue is created. Never from the renderer. */
public final class LyricsPlacementManager {
    public WorldAnchor create(Minecraft mc) {
        if (mc.player == null || mc.level == null) throw new IllegalStateException("No world");
        Vec3 eye = mc.player.getEyePosition();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double near = Math.min(SkyLyricsConfig.MIN_DISTANCE.get(), SkyLyricsConfig.MAX_DISTANCE.get());
        double far = Math.max(SkyLyricsConfig.MIN_DISTANCE.get(), SkyLyricsConfig.MAX_DISTANCE.get());
        double low = Math.min(SkyLyricsConfig.MIN_HEIGHT.get(), SkyLyricsConfig.MAX_HEIGHT.get());
        double high = Math.max(SkyLyricsConfig.MIN_HEIGHT.get(), SkyLyricsConfig.MAX_HEIGHT.get());
        boolean sky = SkyLyricsConfig.VISIBILITY.get() == SkyLyricsConfig.Visibility.SKY;
        Vec3 selected = null;
        double bestScore = -Double.MAX_VALUE;
        Vec3 look = mc.player.getLookAngle();
        double distance = near;
        // Bounded search at birth only. All terrain queries are limited to loaded chunks.
        for (int attempt = 0; attempt < 8; attempt++) {
            distance = near + random.nextDouble() * (far - near);
            double yaw = Math.toRadians(mc.player.getYRot() + random.nextDouble(-28, 28));
            double height = low + random.nextDouble() * (high - low);
            if (sky) height = Math.max(height, low + (high - low) * 0.65);
            double x = eye.x - Math.sin(yaw) * distance;
            double z = eye.z + Math.cos(yaw) * distance;
            BlockPos column = BlockPos.containing(x, eye.y, z);
            if (!mc.level.hasChunkAt(column)) continue;
            double surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            // Sky mode raises a new phrase above treetops; it never moves an existing phrase.
            double y = Math.max(eye.y + height, surface + 3);
            if (y > mc.level.getMaxBuildHeight() + 32) continue;
            Vec3 candidate = new Vec3(x, y, z);
            // Do not raycast through missing chunks or force any chunk load.
            if (!loadedSegment(mc, eye, candidate)) continue;
            double score = candidate.subtract(eye).normalize().dot(look);
            boolean ethereal = SkyLyricsConfig.VISIBILITY.get() == SkyLyricsConfig.Visibility.ETHEREAL;
            boolean visible = ethereal;
            HitResult hit = mc.level.clip(new ClipContext(eye, candidate,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            visible |= hit.getType() == HitResult.Type.MISS;
            double ranked = score + (visible ? 2 : 0);
            if (ranked > bestScore) { selected = candidate; bestScore = ranked; }
            // Prefer a candidate near the current view at birth; never try to repair visibility later.
            if (visible && score >= Math.cos(Math.toRadians(25))) break;
        }
        // If no loaded candidate exists, skip the cue; do not keep retrying it later.
        if (selected == null) return null;
        float style = SkyLyricsConfig.STYLE.get() == SkyLyricsConfig.Style.AMBIENT ? 0.60f : 1f;
        float scale = (float) (SkyLyricsConfig.TEXT_SCALE.get() * style * distance / 24.0);
        return new WorldAnchor(selected.x, selected.y, selected.z, scale, mc.player.getYRot());
    }

    private boolean loadedSegment(Minecraft mc, Vec3 from, Vec3 to) {
        int steps = (int) Math.ceil(from.distanceTo(to) / 4);
        for (int i = 0; i <= steps; i++) {
            if (!mc.level.hasChunkAt(BlockPos.containing(from.lerp(to, i / (double) Math.max(1, steps))))) {
                return false;
            }
        }
        return true;
    }
}
