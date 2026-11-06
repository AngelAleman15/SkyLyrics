package dev.skylyrics.rhythm;

import dev.skylyrics.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class RhythmTest {
    private static final int RATE = 48000, BLOCK = 1024;
    private RhythmState signal(double hz, double amplitude, boolean constant) {
        var analyzer = new RhythmAnalyzer(RATE, RhythmSettings.defaults());
        float[] block = new float[BLOCK]; RhythmState state = RhythmState.silent();
        for (int window = 0; window < 200; window++) {
            for (int i = 0; i < BLOCK; i++) block[i] = (float)(amplitude * (constant ? 1 : Math.sin(2 * Math.PI * hz * (window * BLOCK + i) / RATE)));
            state = analyzer.accept(block, BLOCK, (long)((window + 1) * BLOCK * 1_000_000_000.0 / RATE));
        }
        return state;
    }
    @Test void rmsMatchesKnownSineAndBassFilterRejectsHighTone() {
        var low = signal(80, .5, false); var high = signal(1000, .5, false);
        assertEquals(.5 / Math.sqrt(2), high.rms(), .008);
        assertTrue(low.bassRms() > high.bassRms() * 8);
        assertTrue(low.bassRms() > .2);
    }
    @Test void silenceAndConstantSignalsDoNotInventTransients() {
        var silence = signal(0, 0, true); var constant = signal(0, .2, true); var tone = signal(100, .2, false);
        assertEquals(0, silence.rms()); assertEquals(0, silence.pulse()); assertEquals(0, silence.detectedTransients());
        assertEquals(0, constant.detectedTransients()); assertEquals(0, tone.detectedTransients());
        assertTrue(constant.bassRms() < .00001);
    }
    @Test void lowVolumeAdaptsWithoutPermanentPulseAtHighVolume() {
        var quiet = signal(1000, .001, false); var loud = signal(1000, .8, false);
        assertEquals(loud.intensity(), quiet.intensity(), .03);
        assertEquals(0, quiet.pulse()); assertEquals(0, loud.pulse());
    }
    @Test void realEnergyBurstsProduceEventsWithRefractoryInterval() {
        var analyzer = new RhythmAnalyzer(RATE, RhythmSettings.defaults());
        float[] block = new float[BLOCK]; RhythmState state = RhythmState.silent(); double peak = 0;
        for (int window = 0; window < 190; window++) {
            double amplitude = window > 95 && window % 6 < 2 ? .8 : 0;
            for (int i = 0; i < BLOCK; i++) block[i] = (float)(amplitude * Math.sin(2 * Math.PI * 80 * (window * BLOCK + i) / RATE));
            state = analyzer.accept(block, BLOCK, (long)((window + 1) * BLOCK * 1_000_000_000.0 / RATE));
            peak = Math.max(peak, state.pulse());
        }
        assertTrue(peak > .2); assertTrue(state.detectedTransients() >= 2);
        assertTrue(state.detectedTransients() <= 12, "Events must respect the 180ms refractory period");
    }
    @Test void visualSmoothingHasBoundedAttackReleaseAndNeverChangesAnchorOrLifetime() {
        var controller = new SubtleRhythmController(); var settings = RhythmSettings.defaults();
        var anchor = new WorldAnchor(20, 80, 20, .1f, 10, 5, 3, 5, 1);
        var phrase = new WorldPhrase(1, "Estático", anchor, 0, 9_000_000_000L, 700_000_000, 1_000_000_000);
        long now = 1_000_000_000;
        controller.sample(new RhythmState(.1, .1, 1, 1, now, now, 1, RATE), now, true, settings);
        now += 80_000_000;
        var attack = controller.sample(new RhythmState(.1, .1, 1, 1, now, now, 1, RATE), now, true, settings);
        assertEquals(1 + .02 * (1 - Math.exp(-1)), attack.scale(), .00001);
        for (int i = 0; i < 300; i++) {
            now += 16_000_000;
            var v = controller.sample(new RhythmState(.1, .1, 1, 1, now, now, 1, RATE), now, true, settings);
            assertTrue(v.scale() >= 1 && v.scale() <= 1.02); assertTrue(v.brightness() <= 1.08);
        }
        now += 300_000_000;
        var released = controller.sample(new RhythmState(.1, .1, 1, 1, now, now, 1, RATE), now, false, settings);
        assertTrue(released.scale() < 1.01);
        assertSame(anchor, phrase.anchor()); assertEquals(9_000_000_000L, phrase.expiresNs());
        assertEquals(0xFFF1DC, SubtleRhythmController.brighten(0xFFF1DC, 1));
    }
    @Test void staleOrPausedAudioNeverCreatesFreshPulses() {
        var controller = new SubtleRhythmController(); var state = new RhythmState(.1, .1, 1, 1, 1, 1, 1, RATE);
        controller.sample(RhythmState.silent(), 1_000_000_000L, false, RhythmSettings.defaults());
        assertEquals(1, controller.sample(state, 2_000_000_000L, true, RhythmSettings.defaults()).scale());
        assertEquals(1, controller.sample(state, 3_000_000_000L, false, RhythmSettings.defaults()).scale());
    }
    @Test void monotonicEpochMappingDetectsBackloggedAudioWithoutWallClock() {
        var clock = new AudioClockMapper();
        long epoch = 90_000_000_000L;
        assertEquals(980_000_000L, clock.capturedAt(epoch - 20_000_000, epoch, 1_000_000_000));
        assertEquals(1_980_000_000L, clock.capturedAt(epoch + 980_000_000, epoch + 1_000_000_000, 2_000_000_000));
        long delayed = clock.capturedAt(epoch + 1_980_000_000, epoch + 2_000_000_000, 8_000_000_000L);
        assertEquals(2_980_000_000L, delayed);
        var controller = new SubtleRhythmController();
        var stale = new RhythmState(.2, .2, 1, 1, delayed, 8_000_000_000L, 1, RATE);
        controller.sample(RhythmState.silent(), 7_900_000_000L, false, RhythmSettings.defaults());
        assertEquals(1, controller.sample(stale, 8_000_000_000L, true, RhythmSettings.defaults()).scale());
    }
    @Test void disabledPlatformAndRepeatedCloseAreSafe(@TempDir Path folder) throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"));
        Path target = folder.resolve("uncreated");
        var service = new AudioCaptureService(target, RhythmSettings.defaults());
        service.start();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (service.status().equals("STARTING") && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals("WINDOWS_REQUIRED", service.status()); assertFalse(Files.exists(target));
        service.close(); service.close(); service.start(); assertEquals("STOPPED", service.status());
        assertEquals(0, service.state().pulse());
    }
}
