package dev.skylyrics.rhythm;

/** Streaming 1024-sample RMS, 40–180 Hz bandpass and adaptive energy-transient analysis. */
public final class RhythmAnalyzer {
    private static final int WINDOW = 1024;
    private final RhythmSettings settings;
    private final int sampleRate;
    private final Biquad highPass, lowPass;
    private int count;
    private double total, bassTotal, rmsMean, bassMean, previousRms, fluxMean, fluxVariance, envelope;
    private long firstNs = Long.MIN_VALUE, previousNs, lastPulseNs = Long.MIN_VALUE;
    private long transients;
    private RhythmState latest = RhythmState.silent();

    public RhythmAnalyzer(int sampleRate, RhythmSettings settings) {
        if (sampleRate < 8000 || sampleRate > 384000) throw new IllegalArgumentException("Unsupported sample rate");
        this.sampleRate = sampleRate; this.settings = settings;
        highPass = new Biquad(sampleRate, 40, true); lowPass = new Biquad(sampleRate, 180, false);
    }
    public RhythmState accept(float[] mono, int length, long capturedNs) {
        if (length < 0 || length > mono.length) throw new IllegalArgumentException("Invalid audio block");
        long began = System.nanoTime();
        for (int i = 0; i < length; i++) {
            double value = Float.isFinite(mono[i]) ? Math.max(-1, Math.min(1, mono[i])) : 0;
            double bass = settings.bassReaction() ? lowPass.process(highPass.process(value)) : 0;
            total += value * value; bassTotal += bass * bass;
            if (++count == WINDOW) {
                long stamp = capturedNs - (long)((length - i - 1) * 1_000_000_000.0 / sampleRate);
                finish(Math.sqrt(total / WINDOW), Math.sqrt(bassTotal / WINDOW), stamp, began);
                count = 0; total = 0; bassTotal = 0;
            }
        }
        return latest;
    }
    private void finish(double rms, double bass, long stamp, long began) {
        if (firstNs == Long.MIN_VALUE) { firstNs = stamp; previousNs = stamp; rmsMean = rms; bassMean = bass; }
        double dt = Math.max(.001, Math.min(.25, (stamp - previousNs) / 1_000_000_000.0)); previousNs = stamp;
        double rmsChange = Math.max(0, (rms - previousRms) / Math.max(1e-5, rmsMean));
        double bassChange = Math.max(0, bass / Math.max(1e-5, bassMean) - 1);
        double flux = Math.min(4, Math.max(settings.transientReaction() ? rmsChange : 0,
                settings.bassReaction() ? bassChange : 0));
        double threshold = Math.max(.22, fluxMean + 2 * Math.sqrt(Math.max(0, fluxVariance)));
        boolean warmed = stamp - firstNs > 1_000_000_000L;
        boolean refractory = lastPulseNs != Long.MIN_VALUE && stamp - lastPulseNs < settings.minPulseIntervalMs() * 1_000_000L;
        envelope *= Math.exp(-dt / .22);
        if (rms > 1e-5 && warmed && !refractory && flux * settings.sensitivity() > threshold) {
            envelope = Math.max(envelope, Math.min(1, (flux * settings.sensitivity() - threshold) * 2));
            lastPulseNs = stamp;
            transients++;
        }
        double adaptation = 1 - Math.exp(-dt / 1.5);
        rmsMean += adaptation * (rms - rmsMean); bassMean += adaptation * (bass - bassMean);
        double varianceSpeed = 1 - Math.exp(-dt / 2);
        double deviation = flux - fluxMean;
        fluxMean += varianceSpeed * deviation; fluxVariance += varianceSpeed * (deviation * deviation - fluxVariance);
        previousRms = rms;
        double intensity = rms < 1e-5 ? 0 : Math.min(1, rms / Math.max(1e-5, rmsMean * 1.5));
        long analyzed = System.nanoTime();
        latest = new RhythmState(rms, bass, intensity, Math.max(0, Math.min(1, envelope)), stamp, analyzed,
                Math.max(0, analyzed - began), sampleRate, transients);
    }
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double z1, z2;
        Biquad(int rate, double frequency, boolean highPass) {
            double w = 2 * Math.PI * frequency / rate, cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
            double a0 = 1 + alpha;
            b0 = (highPass ? (1 + cos) : (1 - cos)) / 2 / a0;
            b1 = (highPass ? -(1 + cos) : (1 - cos)) / a0;
            b2 = b0; a1 = -2 * cos / a0; a2 = (1 - alpha) / a0;
        }
        double process(double input) {
            double output = b0 * input + z1;
            z1 = b1 * input - a1 * output + z2; z2 = b2 * input - a2 * output;
            if (Math.abs(z1) < 1e-20) z1 = 0;
            if (Math.abs(z2) < 1e-20) z2 = 0;
            return output;
        }
    }
}
