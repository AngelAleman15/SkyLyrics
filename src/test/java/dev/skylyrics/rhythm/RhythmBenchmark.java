package dev.skylyrics.rhythm;

/** Explicit offline DSP benchmark; never starts capture or claims end-to-end WASAPI latency. */
public final class RhythmBenchmark {
    public static void main(String[] args) {
        var analyzer = new RhythmAnalyzer(48000, RhythmSettings.defaults());
        float[] samples = new float[1024];
        for (int i = 0; i < samples.length; i++) samples[i] = (float)(.3 * Math.sin(2 * Math.PI * 80 * i / 48000));
        for (int i = 0; i < 2000; i++) analyzer.accept(samples, samples.length, i * 21_333_333L);
        var bean = (com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId(), cpu = bean.getCurrentThreadCpuTime();
        long allocation = bean.isThreadAllocatedMemorySupported() ? bean.getThreadAllocatedBytes(id) : 0;
        long began = System.nanoTime(); int blocks = 10000;
        for (int i = 0; i < blocks; i++) analyzer.accept(samples, samples.length, (i + 2000L) * 21_333_333L);
        long elapsed = System.nanoTime() - began;
        System.out.printf(java.util.Locale.ROOT, "DSP only: %d windows; audio=%.2fs; wall=%.2fms; CPU=%.2fms; mean=%.2fµs/window; allocation=%d bytes/window%n",
                blocks, blocks * samples.length / 48000.0, elapsed / 1e6, (bean.getCurrentThreadCpuTime() - cpu) / 1e6,
                elapsed / 1000.0 / blocks, bean.isThreadAllocatedMemorySupported() ? (bean.getThreadAllocatedBytes(id) - allocation) / blocks : -1);
    }
}
