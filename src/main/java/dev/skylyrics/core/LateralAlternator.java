package dev.skylyrics.core;

/** Alternate successful lateral appearances; failed placement does not consume a side. */
public final class LateralAlternator {
    private boolean left = true;
    public PlacementDistribution.Zone next() {
        return left ? PlacementDistribution.Zone.LEFT : PlacementDistribution.Zone.RIGHT;
    }
    public void accept() { left = !left; }
}
