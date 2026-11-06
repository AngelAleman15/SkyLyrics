package dev.skylyrics.rhythm;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in SYSTEM loopback helper. PCM crosses a local pipe, never disk or network. */
public final class AudioCaptureService implements AutoCloseable {
    private final Path folder;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SkyLyrics-Audio"); thread.setDaemon(true); return thread;
    });
    private volatile RhythmSettings settings;
    private volatile RhythmState latest = RhythmState.silent();
    private volatile String status = "STARTING";
    private volatile boolean closed, reset;
    private volatile Process process;
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
    public AudioCaptureService(Path folder, RhythmSettings settings) { this.folder = folder; this.settings = settings; }
    public void start() {
        if (closed || !started.compareAndSet(false, true)) return;
        try { worker.submit(this::read); }
        catch (RejectedExecutionException e) { if (!closed) throw e; }
    }
    public void settings(RhythmSettings settings) { this.settings = settings; }
    public void reset() { reset = true; latest = RhythmState.silent(); }
    public RhythmState state() { return latest; }
    public String status() { return closed ? "STOPPED" : status; }
    private void read() {
        if (closed) return;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) { status = "WINDOWS_REQUIRED"; return; }
        byte[] raw = new byte[8192 * 4]; float[] mono = new float[8192];
        try {
            Path executable = install();
            if (closed) return;
            Process child = new ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process = child;
            if (closed) { child.destroyForcibly(); return; }
            RhythmAnalyzer analyzer = null; RhythmSettings applied = null; int appliedRate = 0;
            AudioClockMapper clock = new AudioClockMapper();
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(child.getInputStream(), 32768))) {
                while (!closed) {
                    if (littleInt(input) != 0x53524d32) throw new IOException("Invalid audio protocol");
                    int kind = littleInt(input);
                    if (kind == 2) {
                        int code = littleInt(input);
                        status = switch (code) { case 1 -> "SYSTEM_CAPTURING"; case 2 -> "NO_OUTPUT_DEVICE_OR_CAPTURE_ERROR";
                            case 3 -> "OUTPUT_DEVICE_CHANGED"; case 4 -> "UNSUPPORTED_MIX_FORMAT";
                            case 5 -> "CAPTURE_STOPPED_RETRYING"; default -> "AUDIO_ERROR"; };
                        latest = RhythmState.silent(); analyzer = null;
                        continue;
                    }
                    if (kind != 1) throw new IOException("Invalid audio frame type");
                    int rate = littleInt(input), count = littleInt(input);
                    long nativeWritten = Long.reverseBytes(input.readLong()), nativeCaptured = Long.reverseBytes(input.readLong());
                    if (rate < 8000 || rate > 384000 || count < 1 || count > mono.length) throw new IOException("Invalid audio frame");
                    input.readFully(raw, 0, count * 4);
                    long receivedNs = System.nanoTime();
                    long capturedNs = clock.capturedAt(nativeCaptured, nativeWritten, receivedNs);
                    if (receivedNs - capturedNs > 500_000_000L) {
                        Arrays.fill(raw, 0, count * 4, (byte)0); latest = RhythmState.silent(); analyzer = null; continue;
                    }
                    for (int i = 0; i < count; i++) {
                        int j = i * 4;
                        mono[i] = Float.intBitsToFloat((raw[j] & 255) | ((raw[j + 1] & 255) << 8) | ((raw[j + 2] & 255) << 16) | ((raw[j + 3] & 255) << 24));
                    }
                    RhythmSettings wanted = settings;
                    if (analyzer == null || reset || rate != appliedRate || !wanted.equals(applied)) {
                        analyzer = new RhythmAnalyzer(rate, wanted); appliedRate = rate; applied = wanted; reset = false;
                    }
                    latest = analyzer.accept(mono, count, capturedNs);
                    Arrays.fill(raw, 0, count * 4, (byte)0); Arrays.fill(mono, 0, count, 0);
                }
            } finally { child.destroy(); }
            if (!closed) status = "AUDIO_BRIDGE_EXITED";
        } catch (Exception e) { if (!closed) status = "AUDIO_BRIDGE_UNAVAILABLE: " + e.getClass().getSimpleName(); }
        finally { Arrays.fill(raw, (byte)0); Arrays.fill(mono, 0); latest = RhythmState.silent(); }
    }
    private Path install() throws Exception {
        Files.createDirectories(folder);
        try (var resource = AudioCaptureService.class.getResourceAsStream("/skylyrics/audio/windows/manifest.json")) {
            if (resource == null) throw new FileNotFoundException("Audio helper manifest missing");
            var manifest = JsonParser.parseString(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (var value : manifest.getAsJsonArray("files")) {
                var file = value.getAsJsonObject(); String name = file.get("name").getAsString();
                if (!Path.of(name).getFileName().toString().equals(name)) throw new IOException("Invalid helper filename");
                String digest = file.get("sha256").getAsString(); Path destination = folder.resolve(name);
                if (Files.isRegularFile(destination) && hash(Files.readAllBytes(destination)).equals(digest)) continue;
                try (var data = AudioCaptureService.class.getResourceAsStream("/skylyrics/audio/windows/" + name)) {
                    if (data == null) throw new FileNotFoundException("Missing helper resource");
                    byte[] bytes = data.readAllBytes();
                    if (!hash(bytes).equals(digest)) throw new IOException("Audio helper checksum mismatch");
                    Path temp = Files.createTempFile(folder, "audio-", ".tmp");
                    try { Files.write(temp, bytes); Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
                    finally { Files.deleteIfExists(temp); }
                }
            }
        }
        return folder.resolve("SkyLyrics.AudioBridge.exe");
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static int littleInt(DataInputStream input) throws IOException { return Integer.reverseBytes(input.readInt()); }
    @Override public void close() {
        closed = true; latest = RhythmState.silent(); status = "STOPPED";
        Process child = process;
        if (child != null) {
            try { child.getOutputStream().write(new byte[] {'q', '\n'}); child.getOutputStream().flush(); }
            catch (IOException ignored) { child.destroy(); }
            child.onExit().orTimeout(1500, TimeUnit.MILLISECONDS).exceptionally(error -> { child.destroyForcibly(); return child; });
        }
        worker.shutdownNow();
    }
}
