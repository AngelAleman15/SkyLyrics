package dev.skylyrics.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorldLyricsTest {
    private final WorldAnchor anchor = new WorldAnchor(1_000_010, 110, -2_000_030, 0.06f, 40);

    @Test
    void phraseSurvivesWalkingTurningAndGoingUnderneathWithoutRespawning() {
        LyricsScene scene = new LyricsScene();
        AtomicInteger placements = new AtomicInteger();
        LyricsSynchronizer timeline = new LyricsSynchronizer(List.of(new LyricCue(0, 30_000, "Aquí")));
        timeline.start(0);
        for (int tick = 0; tick < 600; tick++) {
            long now = tick * 50_000_000L;
            // Playback continues for thirty seconds, with no camera/visibility input.
            timeline.tick(now, true, cue -> {
                placements.incrementAndGet();
                scene.add(cue.text(), anchor, now, cue.endMs() - cue.startMs(), 500, 1000, 4);
            });
            scene.expire(now);
            assertEquals(1, scene.phrases().size());
            WorldPhrase phrase = scene.phrases().get(0);
            assertSame(anchor, phrase.anchor());
            assertEquals(0, phrase.id());
            // Camera-relative positions change; subtracting the camera never changes anchor.
            double cameraX = 1_000_000 + tick * 2;
            double relativeX = phrase.anchor().x() - cameraX;
            assertEquals(1_000_010, relativeX + cameraX);
        }
        assertEquals(1, placements.get());
        scene.expire(30_000_000_000L);
        assertTrue(scene.phrases().isEmpty());
    }

    @Test
    void offscreenPhraseFadesAndExpiresWithoutAnyRenderCall() {
        LyricsScene scene = new LyricsScene();
        scene.add("Lejos", anchor, 0, 6000, 500, 1000, 4);
        WorldPhrase phrase = scene.phrases().get(0);
        assertEquals(0, AnimationController.alpha(phrase, 0));
        assertEquals(1, AnimationController.alpha(phrase, 3_000_000_000L));
        assertEquals(0.5f, AnimationController.alpha(phrase, 5_500_000_000L));
        assertEquals(0, AnimationController.alpha(phrase, 6_000_000_000L));
        scene.expire(6_000_000_000L);
        assertTrue(scene.phrases().isEmpty());
    }

    @Test
    void shortIntervalsHaveSmoothFadesAndNeverNegativeOpacity() {
        WorldPhrase phrase = new WorldPhrase(0, "Corta", anchor, 0,
                200_000_000L, 1_000_000_000L, 1_000_000_000L);
        assertEquals(0.5f, AnimationController.alpha(phrase, 50_000_000L));
        assertEquals(1, AnimationController.alpha(phrase, 100_000_000L));
        assertEquals(0.5f, AnimationController.alpha(phrase, 150_000_000L));
        assertEquals(0, AnimationController.alpha(phrase, -1));
        assertEquals(0, AnimationController.alpha(phrase, 999_000_000L));
    }

    @Test
    void fullSceneDoesNotEvictOrMoveExistingPhrases() {
        LyricsScene scene = new LyricsScene();
        assertTrue(scene.add("A", anchor, 0, 6000, 500, 1000, 1));
        WorldPhrase original = scene.phrases().get(0);
        assertFalse(scene.add("B", new WorldAnchor(0, 100, 0, 1, 0), 1, 6000, 500, 1000, 1));
        assertSame(original, scene.phrases().get(0));
        assertThrows(UnsupportedOperationException.class, () -> scene.phrases().clear());
    }

    @Test
    void hiddenCuesAndAlreadyEndedCuesAreNeverRecreated() {
        AtomicInteger placements = new AtomicInteger();
        LyricsSynchronizer timeline = new LyricsSynchronizer(List.of(
                new LyricCue(0, 1000, "A"), new LyricCue(1000, 2000, "B"),
                new LyricCue(2000, 3000, "C")));
        timeline.start(0);
        timeline.tick(0, false, c -> placements.incrementAndGet());
        timeline.tick(500_000_000, true, c -> placements.incrementAndGet());
        assertEquals(0, placements.get());
        timeline.tick(2_500_000_000L, true, c -> {
            assertEquals("C", c.text());
            assertEquals(500, c.endMs() - c.startMs());
            placements.incrementAndGet();
        });
        timeline.tick(2_900_000_000L, true, c -> fail("Recreated cue"));
        timeline.tick(3_000_000_000L, true, c -> fail("Expired cue"));
        assertEquals(1, placements.get());
        assertFalse(timeline.running());
    }

    @Test
    void worldDisconnectReleasesEveryPhrase() {
        LyricsScene scene = new LyricsScene();
        scene.add("A", anchor, 0, 6000, 500, 1000, 4);
        scene.clear();
        assertTrue(scene.phrases().isEmpty());
    }
}
