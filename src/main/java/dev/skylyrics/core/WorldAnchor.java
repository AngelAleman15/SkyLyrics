package dev.skylyrics.core;

/** Absolute position, initial orientation and full-size bounds. Never camera-relative. */
public record WorldAnchor(double x, double y, double z, float scale, float yawDegrees,
                          float pitchDegrees, float rollDegrees, float widthBlocks, float heightBlocks) {
    public WorldAnchor(double x, double y, double z, float scale, float yawDegrees) {
        this(x, y, z, scale, yawDegrees, 0, 0, 4, 1);
    }

    public WorldAnchor {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(scale) || scale <= 0 || !Float.isFinite(yawDegrees)
                || !Float.isFinite(pitchDegrees) || !Float.isFinite(rollDegrees)
                || !Float.isFinite(widthBlocks) || widthBlocks <= 0
                || !Float.isFinite(heightBlocks) || heightBlocks <= 0) {
            throw new IllegalArgumentException("Invalid world anchor");
        }
    }
}
