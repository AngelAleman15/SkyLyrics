package dev.skylyrics.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlaybackTest {
    @Test void pauseFreezesClockWithoutMovingOrRecreatingAppearance() {
        var clock = new PresentationClock(0);
        clock.update(1_000_000_000, true);
        assertEquals(1_000_000_000, clock.at(5_000_000_000L));
        clock.update(6_000_000_000L, false);
        assertEquals(2_000_000_000L, clock.at(7_000_000_000L));
    }

    @Test void correctionsInsideSameCueNeverReplaceItsAnchor() {
        var scene = new LyricsScene();
        var controller = new MusicLyricsController();
        var anchor = new WorldAnchor(20, 80, 15, .1f, 0);
        controller.replace(List.of(new LyricCue(0, 10_000, "Aquí")), 0);
        AtomicInteger placements = new AtomicInteger();
        java.util.function.BiFunction<LyricCue, Long, WorldPhrase> spawn = (cue, duration) -> {
            placements.incrementAndGet(); scene.add(cue.text(), anchor, 0, duration, 500, 900, 4);
            return scene.phrases().get(0);
        };
        controller.tick(100, 0, 1, true, spawn);
        WorldPhrase same = scene.phrases().get(0);
        controller.tick(7000, 1_000_000_000, 1, true, spawn); // seek forward within cue
        controller.tick(2000, 2_000_000_000, 1, true, spawn); // seek back within cue
        assertSame(same, scene.phrases().get(0));
        assertSame(anchor, same.anchor());
        assertEquals(1, placements.get());
        assertEquals(10_000_000_000L, same.expiresNs());
    }

    @Test void hiddenCueIsNotRecreatedWhenVisibilityReturns() {
        var controller = new MusicLyricsController();
        controller.replace(List.of(new LyricCue(0, 1000, "A"), new LyricCue(1000, 2000, "B")), 0);
        controller.tick(0, 0, 1, false, (a,b) -> fail());
        controller.tick(500, 500_000_000, 1, true, (a,b) -> fail());
        AtomicInteger count = new AtomicInteger();
        controller.tick(1200, 1_200_000_000, 1, true, (a,b) -> { count.incrementAndGet(); return null; });
        controller.tick(1300, 1_300_000_000, 1, true, (a,b) -> fail());
        assertEquals(1, count.get());
    }

    @Test void snapshotHandlesPauseSeekRateAndDeadBridge() {
        var playing = new PlaybackSnapshot("T", "A", "", 6000, 1000, true, 2, 0);
        assertEquals(3000, playing.positionAt(1_000_000_000));
        assertEquals(5000, playing.positionAt(99_000_000_000L));
        var paused = new PlaybackSnapshot("T", "A", "", 6000, 1500, false, 1, 0);
        assertEquals(1500, paused.positionAt(99_000_000_000L));
    }
}
