package dev.skylyrics.core;

/** Absolute world coordinates, initial scale and fixed-mode yaw. Never camera-relative. */
public record WorldAnchor(double x, double y, double z, float scale, float yawDegrees) {
    public WorldAnchor {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(scale) || scale <= 0 || !Float.isFinite(yawDegrees)) {
            throw new IllegalArgumentException("Invalid world anchor");
        }
    }
}
