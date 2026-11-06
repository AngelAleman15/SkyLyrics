package dev.skylyrics.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skylyrics.core.PlaybackSnapshot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One background reader and one Windows child process per connection. */
public final class SpotifyMediaService implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SkyLyrics-GSMTC");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Process process;
    private volatile PlaybackSnapshot snapshot;
    private volatile String status = "STARTING";
    private volatile boolean closed;

    public void start() { worker.submit(this::read); }
    public PlaybackSnapshot snapshot() { return snapshot; }
    public String status() { return status; }

    private void read() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            status = "WINDOWS_REQUIRED";
            return;
        }
        try (var resource = SpotifyMediaService.class.getResourceAsStream("/skylyrics/spotify-gsmtc.ps1")) {
            if (resource == null) throw new IllegalStateException("Missing GSMTC script");
            String script = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null) throw new IllegalStateException("Missing SystemRoot");
            String powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
            Process child = new ProcessBuilder(powershell, "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-EncodedCommand", encoded).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process = child;
            if (closed) { child.destroyForcibly(); return; }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (!closed && (line = reader.readLine()) != null) {
                    if (line.length() > 32_768) continue;
                    try {
                        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                        status = json.get("state").getAsString();
                        if (!"SESSION".equals(status)) { snapshot = null; continue; }
                        long duration = json.get("durationMs").getAsLong();
                        double rate = json.get("rate").getAsDouble();
                        if (!Double.isFinite(rate) || rate < 0 || rate > 4) rate = 1;
                        long position = Math.max(0, json.get("positionMs").getAsLong());
                        boolean playing = json.get("playing").getAsBoolean();
                        long transportMs = Math.max(0, Math.min(2000,
                                System.currentTimeMillis() - json.get("capturedAtMs").getAsLong()));
                        if (playing) position += (long) (transportMs * rate);
                        snapshot = new PlaybackSnapshot(json.get("title").getAsString(),
                                json.get("artist").getAsString(), json.get("album").getAsString(),
                                Math.max(0, duration), position, playing, rate, System.nanoTime());
                    } catch (RuntimeException ignored) { status = "INVALID_SNAPSHOT"; snapshot = null; }
                }
            } finally { child.destroyForcibly(); }
            if (!closed) status = "BRIDGE_EXITED";
        } catch (Exception e) { if (!closed) status = "BRIDGE_UNAVAILABLE"; }
        finally { snapshot = null; }
    }

    @Override public void close() {
        closed = true;
        Process child = process;
        if (child != null) child.destroyForcibly();
        worker.shutdownNow();
        snapshot = null;
        status = "STOPPED";
    }
}
