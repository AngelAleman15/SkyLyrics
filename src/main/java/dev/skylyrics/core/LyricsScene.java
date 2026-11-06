package dev.skylyrics.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Client-thread-owned scene. Lifetime cleanup is independent of rendering and distance. */
public final class LyricsScene {
    private final List<WorldPhrase> phrases = new ArrayList<>();
    private final List<WorldPhrase> readOnly = Collections.unmodifiableList(phrases);
    private long nextId;

    public List<WorldPhrase> phrases() { return readOnly; }

    public void expire(long nowNs) {
        expire(nowNs, nowNs);
    }

    public void expire(long musicalNs, long realNs) {
        phrases.removeIf(phrase -> phrase.expired(musicalNs, realNs));
    }

    public boolean add(String text, WorldAnchor anchor, long nowNs, long durationMs,
                       long fadeInMs, long fadeOutMs, int capacity) {
        return add(text, anchor, nowNs, durationMs, fadeInMs, fadeOutMs, capacity, nowNs);
    }

    public boolean add(String text, WorldAnchor anchor, long nowNs, long durationMs,
                       long fadeInMs, long fadeOutMs, int capacity, long realNs) {
        expire(nowNs, realNs);
        // At capacity, skip the new phrase; never relocate or evict a living phrase.
        if (phrases.size() >= capacity || durationMs <= 0 || text.isBlank()) return false;
        phrases.add(new WorldPhrase(nextId++, text, anchor, nowNs,
                nowNs + durationMs * 1_000_000L,
                fadeInMs * 1_000_000L, fadeOutMs * 1_000_000L));
        return true;
    }

    public void clear() { phrases.clear(); }

    public void finishAll(long nowNs) { phrases.forEach(phrase -> phrase.finish(nowNs)); }

    public void retireAll(long musicalNs, long realNs, long durationMs) {
        phrases.forEach(phrase -> phrase.retire(musicalNs, realNs, durationMs));
    }
}
