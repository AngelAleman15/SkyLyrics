package dev.skylyrics.rhythm;

/** Align unrelated monotonic epochs using the smallest observed IPC delay, never wall time. */
public final class AudioClockMapper {
    private long offsetNs = Long.MAX_VALUE;
    public long capturedAt(long nativeCapturedNs, long nativeWrittenNs, long javaReceivedNs) {
        if (nativeWrittenNs < nativeCapturedNs) throw new IllegalArgumentException("Invalid native audio timestamp");
        long observedOffset = Math.subtractExact(javaReceivedNs, nativeWrittenNs);
        offsetNs = Math.min(offsetNs, observedOffset);
        return Math.addExact(nativeCapturedNs, offsetNs);
    }
}
