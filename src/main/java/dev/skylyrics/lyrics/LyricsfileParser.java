package dev.skylyrics.lyrics;

import dev.skylyrics.core.LyricCue;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.util.*;

/** Safe YAML 1.0 reader. Explicit word timestamps stay explicit; unknown word ends stay null. */
public final class LyricsfileParser {
    public record Parsed(List<LyricCue> cues, List<WordCue> words) { }
    private LyricsfileParser() { }
    public static Parsed parse(String text, long durationMs) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(2_000_000); options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(32); options.setAllowDuplicateKeys(false);
        Object yaml = new Yaml(new SafeConstructor(options)).load(text);
        if (!(yaml instanceof Map<?, ?> root) || !"1.0".equals(root.get("version"))) throw new IllegalArgumentException("LYRICSFILE_VERSION");
        if (root.get("metadata") instanceof Map<?, ?> metadata && metadata.containsKey("offset_ms")
                && number(metadata.get("offset_ms")) != 0) throw new IllegalArgumentException("LYRICSFILE_OFFSET_UNSPECIFIED");
        if (!(root.get("lines") instanceof List<?> items)) return new Parsed(List.of(), List.of());
        List<Map<?, ?>> lines = new ArrayList<>();
        for (Object item : items) if (item instanceof Map<?, ?> line) lines.add(line);
        if (lines.size() > 10_000) throw new IllegalArgumentException("TOO_MANY_LINES");
        lines.sort(Comparator.comparingLong(line -> number(line.get("start_ms"))));
        List<LyricCue> cues = new ArrayList<>(); List<WordCue> words = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Map<?, ?> line = lines.get(i);
            if (!(line.get("text") instanceof String body)) throw new IllegalArgumentException("LYRICSFILE_TEXT");
            long start = number(line.get("start_ms"));
            long end = line.containsKey("end_ms") ? number(line.get("end_ms"))
                    : i + 1 < lines.size() ? number(lines.get(i + 1).get("start_ms")) : durationMs;
            if (start < 0 || start >= durationMs || end <= start) continue;
            int lineIndex = cues.size();
            cues.add(new LyricCue(start, Math.min(end, durationMs), body));
            if (line.get("words") instanceof List<?> timedWords) {
                for (Object entry : timedWords) {
                    if (!(entry instanceof Map<?, ?> word) || !(word.get("text") instanceof String value)) throw new IllegalArgumentException("LYRICSFILE_WORD");
                    long wordStart = number(word.get("start_ms"));
                    Long wordEnd = word.containsKey("end_ms") ? number(word.get("end_ms")) : null;
                    if (wordStart < start || wordStart > end || (wordEnd != null && (wordEnd < wordStart || wordEnd > end))) throw new IllegalArgumentException("LYRICSFILE_WORD_TIMING");
                    words.add(new WordCue(lineIndex, value, wordStart, wordEnd));
                }
            }
        }
        return new Parsed(List.copyOf(cues), List.copyOf(words));
    }
    private static long number(Object value) {
        if (!(value instanceof Number number) || number.doubleValue() != number.longValue()) throw new IllegalArgumentException("LYRICSFILE_INTEGER_TIMING");
        return number.longValue();
    }
}
