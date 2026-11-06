package dev.skylyrics.core;

import java.util.random.RandomGenerator;

/** Select once per appearance, so terrain scoring cannot erase the requested distribution. */
public final class PlacementDistribution {
    public enum Mode { CINEMATIC, SIDES, RANDOM }
    public enum Zone { LEFT, RIGHT, ELEVATED, FRONT, RANDOM }
    private PlacementDistribution() { }

    public static Zone choose(Mode mode, double sideBias, double left, double right,
                              double elevated, double front, RandomGenerator random) {
        if (mode == Mode.RANDOM) return Zone.RANDOM;
        if (mode == Mode.SIDES) {
            double sum = left + right;
            return random.nextDouble() < (sum > 0 ? left / sum : 0.5) ? Zone.LEFT : Zone.RIGHT;
        }
        left *= sideBias;
        right *= sideBias;
        double total = left + right + elevated + front;
        if (total <= 0) return random.nextBoolean() ? Zone.LEFT : Zone.RIGHT;
        double roll = random.nextDouble() * total;
        if (roll < left) return Zone.LEFT;
        if (roll < left + right) return Zone.RIGHT;
        if (roll < left + right + elevated) return Zone.ELEVATED;
        return Zone.FRONT;
    }

    public static double yawOffset(Zone zone, Mode mode, RandomGenerator random) {
        double start = mode == Mode.SIDES ? 40 : 32;
        double end = mode == Mode.SIDES ? 78 : 64;
        return switch (zone) {
            case LEFT -> -between(start, end, random);
            case RIGHT -> between(start, end, random);
            case ELEVATED -> between(-38, 38, random);
            case FRONT -> between(-16, 16, random);
            case RANDOM -> between(-180, 180, random);
        };
    }

    public static double between(double low, double high, RandomGenerator random) {
        return low + random.nextDouble() * (high - low);
    }
}
