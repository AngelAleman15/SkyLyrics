package dev.skylyrics.core;

import com.google.gson.JsonObject;

/** Validates read-only bridge messages without depending on Minecraft or Windows. */
public final class MediaSnapshotCodec {
    private MediaSnapshotCodec() { }
    public static PlaybackSnapshot decode(JsonObject json, long wallMs, long nowNs) {
        if (!"SESSION".equals(json.get("state").getAsString())) return null;
        long duration = Math.max(0, json.get("durationMs").getAsLong());
        double rate = json.get("rate").getAsDouble();
        if (!Double.isFinite(rate) || rate < 0 || rate > 4) rate = 1;
        long position = Math.max(0, json.get("positionMs").getAsLong());
        boolean playing = json.get("playing").getAsBoolean();
        long transport = Math.max(0, Math.min(2000, wallMs - json.get("capturedAtMs").getAsLong()));
        if (playing) position = (long) Math.min(Long.MAX_VALUE, (double) position + transport * rate);
        if (duration > 0) position = Math.min(duration, position);
        return new PlaybackSnapshot(json.get("title").getAsString(), json.get("artist").getAsString(),
                json.get("album").getAsString(), duration, position, playing, rate, nowNs);
    }
}
