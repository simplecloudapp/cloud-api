package app.simplecloud.api.internal.event.player;

import build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent;
import build.buf.gen.simplecloud.player.v2.PlayerKickEvent;
import build.buf.gen.simplecloud.player.v2.PlayerLoginEvent;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Decodes events from raw field numbers, which is what the controller sends, so the test fails
 * if a public getter reads the wrong field.
 */
class PlayerEventFieldsTest {

    @Test
    void loginReadsProxyNameFromField3() throws Exception {
        PlayerLoginEventImpl event = new PlayerLoginEventImpl(
                PlayerLoginEvent.parseFrom(fields(3, "proxy-1")), null, null);

        assertEquals("proxy-1", event.getProxyName());
    }

    @Test
    void disconnectReadsProxyNameFromField3AndServerNameFromField4() throws Exception {
        PlayerDisconnectEventImpl event = new PlayerDisconnectEventImpl(
                PlayerDisconnectEvent.parseFrom(fields(3, "proxy-1", 4, "lobby-1")), null, null);

        assertEquals("proxy-1", event.getProxyName());
        assertEquals("lobby-1", event.getServerName());
    }

    @Test
    void serverSwitchReadsPreviousServerFromField3AndNewServerFromField4() throws Exception {
        PlayerServerSwitchEventImpl event = new PlayerServerSwitchEventImpl(
                PlayerServerSwitchEvent.parseFrom(fields(3, "lobby-1", 4, "game-2")), null, null);

        assertEquals("lobby-1", event.getPreviousServerName());
        assertEquals("game-2", event.getNewServerName());
    }

    @Test
    void firstServerSwitchHasNoPreviousServer() throws Exception {
        PlayerServerSwitchEventImpl event = new PlayerServerSwitchEventImpl(
                PlayerServerSwitchEvent.parseFrom(fields(4, "lobby-1")), null, null);

        assertNull(event.getPreviousServerName());
        assertEquals("lobby-1", event.getNewServerName());
    }

    @Test
    void kickReadsServerNameFromField3() throws Exception {
        PlayerKickEventImpl event = new PlayerKickEventImpl(
                PlayerKickEvent.parseFrom(fields(3, "lobby-1")), null, null);

        assertEquals("lobby-1", event.getServerName());
    }

    /** Encodes string fields as protobuf wire format: a tag byte, a length byte, the UTF-8 bytes. */
    private static byte[] fields(Object... numbersAndValues) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < numbersAndValues.length; i += 2) {
            byte[] value = ((String) numbersAndValues[i + 1]).getBytes(StandardCharsets.UTF_8);
            out.write(((int) numbersAndValues[i] << 3) | 2);
            out.write(value.length);
            out.writeBytes(value);
        }
        return out.toByteArray();
    }
}
