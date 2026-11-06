package dev.skylyrics.rhythm;

/** Tiny immutable result; never contains PCM samples or track credentials. */
public record RhythmState(double rms, double bassRms, double intensity, double pulse,
                          long capturedNs, long analyzedNs, long analysisNanos, int sampleRate, long detectedTransients) {
    public RhythmState(double rms, double bassRms, double intensity, double pulse, long capturedNs,
                       long analyzedNs, long analysisNanos, int sampleRate) {
        this(rms, bassRms, intensity, pulse, capturedNs, analyzedNs, analysisNanos, sampleRate, 0);
    }
    public static RhythmState silent() { return new RhythmState(0, 0, 0, 0, 0, 0, 0, 0); }
}
