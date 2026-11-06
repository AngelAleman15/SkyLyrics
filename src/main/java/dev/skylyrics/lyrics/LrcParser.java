package dev.skylyrics.lyrics;

import dev.skylyrics.core.LyricCue;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Line timestamps only. Plain text never gets invented timing. */
public final class LrcParser {
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)]\\s*$");
    private LrcParser() { }

    public static List<LyricCue> parse(String lrc, long durationMs) {
        if (lrc == null || lrc.length() > 2_000_000 || durationMs <= 0) return List.of();
        long offset = 0;
        var offsetMatch = OFFSET.matcher(lrc);
        if (offsetMatch.find()) offset = Long.parseLong(offsetMatch.group(1));
        TreeMap<Long, String> lines = new TreeMap<>();
        for (String line : lrc.replace("\uFEFF", "").split("\\R")) {
            var matcher = TIME.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find() && matcher.start() == end) {
                String fraction = matcher.group(3);
                long ms = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
                long stamp = Long.parseLong(matcher.group(1)) * 60_000
                        + Long.parseLong(matcher.group(2)) * 1000 + ms - offset;
                times.add(Math.max(0, stamp));
                end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String text = line.substring(end).replaceAll("<\\d{1,3}:[0-5]\\d(?:[.:]\\d{1,3})?>", "").strip();
            if (text.length() > 2048) text = text.substring(0, 2048);
            for (long stamp : times) {
                if (stamp < durationMs) lines.merge(stamp, text, (a, b) -> a.isBlank() ? b : b.isBlank() ? a : a + " · " + b);
            }
        }
        List<LyricCue> result = new ArrayList<>();
        var entries = new ArrayList<>(lines.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            long start = entries.get(i).getKey();
            long end = i + 1 < entries.size() ? entries.get(i + 1).getKey() : durationMs;
            if (end > start) result.add(new LyricCue(start, end, entries.get(i).getValue()));
        }
        return List.copyOf(result);
    }
}
