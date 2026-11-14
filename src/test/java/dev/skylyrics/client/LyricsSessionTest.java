package dev.skylyrics.client;
import dev.skylyrics.core.PlaybackSnapshot;
import dev.skylyrics.lyrics.LyricsResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class LyricsSessionTest {
    @TempDir Path root;
    private PlaybackSnapshot track(String title) { return new PlaybackSnapshot(title,"Artist","",6000,0,true,1,0); }
    private LyricsSession.Loaded await(LyricsSession session) throws Exception {
        long deadline=System.nanoTime()+2_000_000_000L;
        while(session.loaded()==null && System.nanoTime()<deadline) Thread.sleep(5);
        assertNotNull(session.loaded()); return session.loaded();
    }
    @Test void asynchronousReplacementNeverPublishesPreviousTrack() throws Exception {
        Files.createDirectories(root.resolve("imports"));
        Files.writeString(root.resolve("imports/a.lrc"),"[00:00.00]A");
        Files.writeString(root.resolve("imports/b.lrc"),"[00:00.00]B");
        try(var session=new LyricsSession(root)) {
            session.request(track("A"),"a.lrc",false);
            session.request(track("B"),"b.lrc",false);
            var loaded=await(session);
            assertEquals(track("B").trackKey(),loaded.key());
            assertEquals(LyricsResult.State.SYNCED,loaded.result().state());
            assertEquals("B",loaded.result().cues().getFirst().text());
        }
    }
    @Test void missingLocalFileProducesSafeErrorWithoutNetwork() throws Exception {
        try(var session=new LyricsSession(root)) {
            session.request(track("Missing"),"missing.lrc",false);
            assertEquals(LyricsResult.State.ERROR,await(session).result().state());
        }
    }
}
