package dev.skylyrics.core;

/** Conservative spatial and angular bounds, evaluated only at creation. */
public final class PlacementGeometry {
    private PlacementGeometry() { }
    private static double radius(WorldAnchor a) { return Math.hypot(a.widthBlocks(), a.heightBlocks()) / 2; }

    public static boolean overlaps(WorldAnchor a, WorldAnchor b, double eyeX, double eyeY, double eyeZ) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        double separation = radius(a) + radius(b) + 1.5;
        if (dx * dx + dy * dy + dz * dz < separation * separation) return true;
        double ax = a.x() - eyeX, ay = a.y() - eyeY, az = a.z() - eyeZ;
        double bx = b.x() - eyeX, by = b.y() - eyeY, bz = b.z() - eyeZ;
        double ad = Math.sqrt(ax * ax + ay * ay + az * az), bd = Math.sqrt(bx * bx + by * by + bz * bz);
        if (ad < 0.01 || bd < 0.01) return true;
        double dot = Math.max(-1, Math.min(1, (ax * bx + ay * by + az * bz) / (ad * bd)));
        double angular = Math.atan2(radius(a), ad) + Math.atan2(radius(b), bd) + Math.toRadians(2);
        return Math.acos(dot) < angular;
    }
}
