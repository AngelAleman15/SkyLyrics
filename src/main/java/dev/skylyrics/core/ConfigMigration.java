package dev.skylyrics.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.regex.Pattern;

/** Rename only known 0.2 keys, preserving existing values before Forge validates the new schema. */
public final class ConfigMigration {
    private ConfigMigration() { }
    public static void migrate(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return;
        String original = Files.readString(path), migrated = original;
        var names = Map.of("billboard", "enableBillboarding", "opacity", "textOpacity",
                "fadeInMs", "fadeInDuration", "fadeOutMs", "fadeOutDuration", "maxPhrases", "maxVisibleLyrics");
        for (var entry : names.entrySet()) {
            if (Pattern.compile("(?m)^\\s*" + entry.getValue() + "\\s*=").matcher(original).find()) continue;
            migrated = migrated.replaceAll("(?m)^(\\s*)" + entry.getKey() + "(\\s*=)",
                    "$1" + entry.getValue() + "$2");
        }
        if (migrated.equals(original)) return;
        Path temp = Files.createTempFile(path.getParent(), "skylyrics-migrate-", ".tmp");
        try {
            Files.writeString(temp, migrated);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
