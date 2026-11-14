package dev.skylyrics.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LateralAlternatorTest {
    @Test void alternatesOnlyAcceptedAppearances() {
        var sides = new LateralAlternator();
        assertEquals(PlacementDistribution.Zone.LEFT, sides.next());
        assertEquals(PlacementDistribution.Zone.LEFT, sides.next());
        sides.accept();
        assertEquals(PlacementDistribution.Zone.RIGHT, sides.next());
        sides.accept();
        assertEquals(PlacementDistribution.Zone.LEFT, sides.next());
    }
}
