package dev.skylyrics.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LrcParserTest {
    @Test void parsesMultipleStampsFractionsMetadataOffsetAndBlankIntervals() {
        var cues = LrcParser.parse("\uFEFF[ar:Original]\n[offset:100]\n[00:01.2][00:03.456]Hola\n[00:04.00]\n[00:05]Fin", 6000);
        assertEquals(4, cues.size());
        assertEquals(1100, cues.get(0).startMs());
        assertEquals(3356, cues.get(1).startMs());
        assertEquals(3900, cues.get(2).startMs());
        assertTrue(cues.get(2).text().isBlank());
        assertEquals(6000, cues.get(3).endMs());
    }
    @Test void plainLyricsNeverGetInventedTimestamps() {
        assertTrue(LrcParser.parse("Una línea sin tiempos", 6000).isEmpty());
        assertTrue(LrcParser.parse("[00:01]Una línea", 0).isEmpty());
    }
    @Test void simultaneousLinesAreMergedAndWordTimingIsNotSimulated() {
        var cues = LrcParser.parse("[00:01]A\n[00:01]B\n[00:02]<00:02.00>Una <00:02.50>frase", 5000);
        assertEquals(2, cues.size());
        assertEquals("A · B", cues.get(0).text());
        assertEquals("Una frase", cues.get(1).text());
    }
}
