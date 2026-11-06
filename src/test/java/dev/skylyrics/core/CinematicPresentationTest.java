package dev.skylyrics.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class CinematicPresentationTest {
    @TempDir Path folder;

    @Test void cinematicDistributionHonorsConfiguredWeightsWithoutCenterBias() {
        var counts = new EnumMap<PlacementDistribution.Zone, Integer>(PlacementDistribution.Zone.class);
        var random = new Random(4927);
        for (int i = 0; i < 100_000; i++) {
            var zone = PlacementDistribution.choose(PlacementDistribution.Mode.CINEMATIC, 1, 40, 40, 10, 10, random);
            counts.merge(zone, 1, Integer::sum);
            double offset = PlacementDistribution.yawOffset(zone, PlacementDistribution.Mode.CINEMATIC, random);
            if (zone == PlacementDistribution.Zone.LEFT) assertTrue(offset >= -64 && offset <= -32);
            if (zone == PlacementDistribution.Zone.RIGHT) assertTrue(offset >= 32 && offset <= 64);
        }
        assertEquals(40_000, counts.get(PlacementDistribution.Zone.LEFT), 700);
        assertEquals(40_000, counts.get(PlacementDistribution.Zone.RIGHT), 700);
        assertEquals(10_000, counts.get(PlacementDistribution.Zone.ELEVATED), 500);
        assertEquals(10_000, counts.get(PlacementDistribution.Zone.FRONT), 500);
    }

    @Test void sidesModeHasNoFrontalBirthsAndZeroWeightsHaveSafeFallback() {
        var random = new Random(382);
        for (int i = 0; i < 500; i++) {
            var zone = PlacementDistribution.choose(PlacementDistribution.Mode.SIDES, 0, 0, 100, 100, 100, random);
            assertEquals(PlacementDistribution.Zone.RIGHT, zone);
            assertEquals(PlacementDistribution.Zone.RANDOM,
                    PlacementDistribution.choose(PlacementDistribution.Mode.RANDOM, 0, 0, 0, 0, 0, random));
            var fallback = PlacementDistribution.choose(PlacementDistribution.Mode.CINEMATIC, 0, 0, 0, 0, 0, random);
            assertTrue(fallback == PlacementDistribution.Zone.LEFT || fallback == PlacementDistribution.Zone.RIGHT);
        }
    }

    @Test void angularOverlapIsRejectedEvenWhenAnchorsAreFarApart() {
        var a = new WorldAnchor(20, 5, 30, .1f, 0);
        assertTrue(PlacementGeometry.overlaps(a, new WorldAnchor(40, 10, 60, .1f, 0), 0, 0, 0));
        assertTrue(PlacementGeometry.overlaps(a, new WorldAnchor(21, 5, 30, .1f, 0), 0, 0, 0));
        assertFalse(PlacementGeometry.overlaps(a, new WorldAnchor(-20, 5, 30, .1f, 0), 0, 0, 0));
    }

    @Test void scaleAndOpacityFreezeWithMusicalClockWithoutChangingOrientation() {
        var anchor = new WorldAnchor(30, 90, -20, .14f, 35, -20, 4, 8, 2);
        var phrase = new WorldPhrase(1, "Quieto", anchor, 0, 6_000_000_000L, 700_000_000, 1_000_000_000);
        var clock = new PresentationClock(0);
        clock.update(350_000_000, true);
        assertEquals(.975f, AnimationController.scale(phrase, clock.at(10_000_000_000L)), .0001);
        assertEquals(.5f, AnimationController.alpha(phrase, clock.at(10_000_000_000L)), .0001);
        assertSame(anchor, phrase.anchor());
        assertEquals(4, phrase.anchor().rollDegrees());
        clock.update(10_000_000_000L, false);
        assertEquals(1f, AnimationController.scale(phrase, clock.at(10_350_000_000L)));
    }

    @Test void trackChangeRetiresOffscreenPhrasesEvenIfNewSongIsPaused() {
        var scene = new LyricsScene();
        var anchor = new WorldAnchor(30, 90, -20, .14f, 35, -20, 4, 8, 2);
        scene.add("Anterior", anchor, 0, 6000, 700, 1000, 4);
        var phrase = scene.phrases().get(0);
        long music = 3_000_000_000L, real = 9_000_000_000L;
        scene.retireAll(music, real, 350);
        assertEquals(1, AnimationController.alpha(phrase, music, real));
        assertEquals(.5f, AnimationController.alpha(phrase, music, real + 175_000_000));
        phrase.alignDeadline(music, 60_000); // Late correction must never revive a retired appearance.
        scene.retireAll(music, real + 300_000_000, 350); // Repeated notifications cannot extend retirement.
        scene.expire(music, real + 350_000_000);
        assertTrue(scene.phrases().isEmpty());
        assertEquals(0, AnimationController.alpha(phrase, music, real + 350_000_000));
        assertSame(anchor, phrase.anchor());
    }

    @Test void configUpgradePreservesExistingChoicesAndIsIdempotent() throws Exception {
        Path path = folder.resolve("skylyrics-client.toml");
        Files.writeString(path, "billboard = false\nopacity = 0.7\nfadeInMs = 450\nfadeOutMs = 850\nmaxPhrases = 3\nminDistance = 24.0\n");
        ConfigMigration.migrate(path);
        String migrated = Files.readString(path);
        assertTrue(migrated.contains("enableBillboarding = false"));
        assertTrue(migrated.contains("textOpacity = 0.7"));
        assertTrue(migrated.contains("fadeInDuration = 450"));
        assertTrue(migrated.contains("fadeOutDuration = 850"));
        assertTrue(migrated.contains("maxVisibleLyrics = 3"));
        assertTrue(migrated.contains("minDistance = 24.0"));
        ConfigMigration.migrate(path);
        assertEquals(migrated, Files.readString(path));
    }
}
