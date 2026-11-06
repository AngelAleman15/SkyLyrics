package dev.skylyrics.lyrics;

import dev.skylyrics.core.PlaybackSnapshot;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative identity gate. Normalization broadens retrieval, never removes version checks. */
public final class TrackMatcher {
    private static final Pattern SUFFIX = Pattern.compile("\\([^)]*\\)|\\[[^]]*]|\\s[-–—]\\s.*$");
    private static final Pattern FEATURES = Pattern.compile("(?i)\\b(?:feat\\.?|ft\\.?|featuring|with)\\s+");
    public record Assessment(boolean accepted, double score, String reason) { }
    private TrackMatcher() { }
    public static String canonical(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    public static String baseTitle(String title) {
        var matcher = SUFFIX.matcher(title);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            String suffix = canonical(matcher.group());
            boolean version = suffix.matches(".*\\b(?:remaster(?:ed|ing)?|live|remix|mix|version|edit|acoustic|unplugged|demo|extended|feat|ft|featuring|with|cover|karaoke|instrumental)\\b.*");
            matcher.appendReplacement(cleaned, java.util.regex.Matcher.quoteReplacement(version ? "" : matcher.group()));
        }
        matcher.appendTail(cleaned);
        return canonical(cleaned.toString().replaceAll("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*", ""));
    }
    public static String primaryArtist(String artist) { return artist.split("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with|and)\\s+|\\s*&\\s*|[,;]", 2)[0].strip(); }
    private static Set<String> artists(String artist, String title) {
        var result = new HashSet<String>();
        for (String item : FEATURES.split(artist)) {
            for (String name : item.split("(?i)[,;&]|\\s+and\\s+")) if (!canonical(name).isBlank()) result.add(canonical(name));
        }
        var feature = FEATURES.matcher(title);
        if (feature.find()) {
            String tail = title.substring(feature.end()).replaceAll("[\\])].*", "");
            for (String name : tail.split("[,;&]")) if (!canonical(name).isBlank()) result.add(canonical(name));
        }
        return result;
    }
    private static Set<String> versions(String title, String album) {
        Set<String> tags = new HashSet<>();
        var suffix = SUFFIX.matcher(title);
        StringBuilder text = new StringBuilder();
        while (suffix.find()) text.append(' ').append(suffix.group());
        String a = canonical(album);
        if (a.matches(".*\\b(?:live at|live from|in concert|unplugged)\\b.*") || a.startsWith("live ") || a.equals("live")) text.append(' ').append(album);
        String marked = canonical(text.toString());
        for (String tag : List.of("live", "acoustic", "unplugged", "remix", "mix", "cover", "karaoke", "instrumental", "demo", "extended", "radio edit", "clean", "explicit")) {
            if ((" " + marked + " ").contains(" " + tag + " ")) tags.add(tag);
        }
        return tags;
    }
    private static String performanceSuffix(String title) {
        var matcher = SUFFIX.matcher(title);
        var result = new ArrayList<String>();
        while (matcher.find()) {
            String suffix = canonical(matcher.group());
            if (suffix.matches(".*\\b(?:live|acoustic|unplugged|remix|mix|cover|karaoke|instrumental|demo|extended|edit|clean|explicit)\\b.*")) result.add(suffix);
        }
        return String.join(" ", result);
    }
    public static Assessment assess(PlaybackSnapshot original, String title, String artist, String album, double seconds) {
        if (!baseTitle(original.title()).equals(baseTitle(title))) return reject("TITLE_MISMATCH");
        String primary = canonical(primaryArtist(original.artist())), candidatePrimary = canonical(primaryArtist(artist));
        Set<String> expected = artists(original.artist(), original.title()), actual = artists(artist, title);
        boolean sameFullArtist = canonical(original.artist()).equals(canonical(artist));
        if (!actual.equals(expected)) return reject("ARTIST_OR_COLLABORATION_MISMATCH");
        if (!versions(original.title(), original.album()).equals(versions(title, album))) return reject("PERFORMANCE_VERSION_MISMATCH");
        String originalVersion = performanceSuffix(original.title()), candidateVersion = performanceSuffix(title);
        if (!originalVersion.isBlank() && !candidateVersion.isBlank() && !originalVersion.equals(candidateVersion)) return reject("SPECIFIC_PERFORMANCE_MISMATCH");
        boolean albumMatches = !original.album().isBlank() && canonical(original.album()).equals(canonical(album));
        double delta = Math.abs(original.durationMs() / 1000.0 - seconds);
        double tolerance = albumMatches ? Math.max(3, Math.min(5, seconds * .01)) : 2;
        if (!Double.isFinite(seconds) || seconds <= 0 || delta > tolerance) return reject("DURATION_MISMATCH delta=" + delta + "s");
        double score = 90 + (albumMatches ? 12 : 0) + (sameFullArtist ? 2 : 0)
                + (primary.equals(candidatePrimary) ? 1 : 0) + (canonical(original.title()).equals(canonical(title)) ? 4 : 0) + Math.max(0, 4 - delta);
        return new Assessment(true, score, albumMatches ? "TITLE_ARTIST_ALBUM_DURATION" : "TITLE_ARTIST_DURATION_ALBUM_DIFFERS");
    }
    private static Assessment reject(String reason) { return new Assessment(false, 0, reason); }
}
