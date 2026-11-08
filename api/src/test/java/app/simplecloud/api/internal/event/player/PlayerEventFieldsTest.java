package app.simplecloud.api.internal.event.player;

import app.simplecloud.api.player.PlayerApi;
import build.buf.gen.simplecloud.player.v2.PlayerDisconnectEvent;
import build.buf.gen.simplecloud.player.v2.PlayerKickEvent;
import build.buf.gen.simplecloud.player.v2.PlayerLoginEvent;
import build.buf.gen.simplecloud.player.v2.PlayerServerSwitchEvent;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Decodes events from raw field numbers, which is what the controller sends, so the test fails
 * if a public getter reads the wrong field.
 */
class PlayerEventFieldsTest {

    /** These tests never call {@code getPlayer()}, the only method that needs the API or NATS. */
    private static final PlayerApi NO_PLAYER_API = null;
    private static final Connection NO_NATS_CONNECTION = null;

    @Test
    void loginReadsProxyNameFromField3() throws IOException {
        ByteString bytes = encode(new StringField(3, "proxy-1"));
        PlayerLoginEvent proto = PlayerLoginEvent.parseFrom(bytes);

        PlayerLoginEventImpl event = new PlayerLoginEventImpl(proto, NO_PLAYER_API, NO_NATS_CONNECTION);

        assertEquals("proxy-1", event.getProxyName());
    }

    @Test
    void disconnectReadsProxyNameFromField3AndServerNameFromField4() throws IOException {
        ByteString bytes = encode(new StringField(3, "proxy-1"), new StringField(4, "lobby-1"));
        PlayerDisconnectEvent proto = PlayerDisconnectEvent.parseFrom(bytes);

        PlayerDisconnectEventImpl event = new PlayerDisconnectEventImpl(proto, NO_PLAYER_API, NO_NATS_CONNECTION);

        assertEquals("proxy-1", event.getProxyName());
        assertEquals("lobby-1", event.getServerName());
    }

    @Test
    void serverSwitchReadsPreviousServerFromField3AndNewServerFromField4() throws IOException {
        ByteString bytes = encode(new StringField(3, "lobby-1"), new StringField(4, "game-2"));
        PlayerServerSwitchEvent proto = PlayerServerSwitchEvent.parseFrom(bytes);

        PlayerServerSwitchEventImpl event = new PlayerServerSwitchEventImpl(proto, NO_PLAYER_API, NO_NATS_CONNECTION);

        assertEquals("lobby-1", event.getPreviousServerName());
        assertEquals("game-2", event.getNewServerName());
    }

    @Test
    void firstServerSwitchHasNoPreviousServer() throws IOException {
        ByteString bytes = encode(new StringField(4, "lobby-1"));
        PlayerServerSwitchEvent proto = PlayerServerSwitchEvent.parseFrom(bytes);

        PlayerServerSwitchEventImpl event = new PlayerServerSwitchEventImpl(proto, NO_PLAYER_API, NO_NATS_CONNECTION);

        assertNull(event.getPreviousServerName());
        assertEquals("lobby-1", event.getNewServerName());
    }

    @Test
    void kickReadsServerNameFromField3() throws IOException {
        ByteString bytes = encode(new StringField(3, "lobby-1"));
        PlayerKickEvent proto = PlayerKickEvent.parseFrom(bytes);

        PlayerKickEventImpl event = new PlayerKickEventImpl(proto, NO_PLAYER_API, NO_NATS_CONNECTION);

        assertEquals("lobby-1", event.getServerName());
    }

    private record StringField(int number, String value) {
    }

    private static ByteString encode(StringField... fields) throws IOException {
        ByteString.Output output = ByteString.newOutput();
        CodedOutputStream stream = CodedOutputStream.newInstance(output);
        for (StringField field : fields) {
            stream.writeString(field.number(), field.value());
        }
        stream.flush();
        return output.toByteString();
    }
}
