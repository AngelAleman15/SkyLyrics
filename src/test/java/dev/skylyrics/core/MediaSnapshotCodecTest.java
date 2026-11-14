package dev.skylyrics.core;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MediaSnapshotCodecTest {
    private JsonObject message() {
        return JsonParser.parseString("{\"state\":\"SESSION\",\"title\":\"A\",\"artist\":\"B\",\"album\":\"C\",\"durationMs\":60000,\"positionMs\":1000,\"playing\":true,\"rate\":1,\"capturedAtMs\":9000}").getAsJsonObject();
    }
    @Test void playingAccountsForTransportAndPauseDoesNot() {
        var json=message();
        assertEquals(2000, MediaSnapshotCodec.decode(json,10000,0).positionMs());
        json.addProperty("playing",false);
        var paused=MediaSnapshotCodec.decode(json,10000,0);
        assertEquals(1000,paused.positionAt(1_000_000_000));
    }
    @Test void seekAndTrackChangeReplaceSnapshotWithoutExtrapolatingOldTrack() {
        var json=message(); var before=MediaSnapshotCodec.decode(json,9000,0);
        json.addProperty("positionMs",40000);
        assertEquals(40000,MediaSnapshotCodec.decode(json,9000,0).positionMs());
        json.addProperty("title","New song"); json.addProperty("positionMs",0);
        var after=MediaSnapshotCodec.decode(json,9000,0);
        assertNotEquals(before.trackKey(),after.trackKey()); assertEquals(0,after.positionMs());
    }
    @Test void noSessionAndMalformedMessageCannotCreateLyrics() {
        assertNull(MediaSnapshotCodec.decode(JsonParser.parseString("{\"state\":\"NO_SESSION\"}").getAsJsonObject(),0,0));
        var json=message();json.remove("title");
        assertThrows(RuntimeException.class,()->MediaSnapshotCodec.decode(json,9000,0));
    }
    @Test void invalidRateAndPositionAreBounded() {
        var json=message();json.addProperty("rate",-8);json.addProperty("positionMs",90000);
        var value=MediaSnapshotCodec.decode(json,20000,0);
        assertEquals(1,value.rate());assertEquals(60000,value.positionMs());
    }
}
