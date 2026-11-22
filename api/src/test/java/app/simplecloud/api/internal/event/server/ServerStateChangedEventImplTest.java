package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.server.ServerState;
import build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerStateChangedEventImplTest {

    @Test
    void mapsStatesAndTimestampInSeconds() {
        ServerStateChangedEventImpl event = new ServerStateChangedEventImpl(ServerStateChangedEvent.newBuilder()
                .setServerId("server-1")
                .setOldState(build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_STARTING)
                .setNewState(build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_AVAILABLE)
                .setTimestamp(1_700_000_000L)
                .build());

        assertEquals("server-1", event.getServerId());
        assertEquals(ServerState.STARTING, event.getOldState());
        assertEquals(ServerState.AVAILABLE, event.getNewState());
        assertEquals("2023-11-14T22:13:20Z", event.getTimestamp());
    }

    @Test
    void unspecifiedStateBecomesUnknown() {
        ServerStateChangedEventImpl event = new ServerStateChangedEventImpl(ServerStateChangedEvent.newBuilder()
                .setNewState(build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_UNSPECIFIED)
                .build());

        assertEquals(ServerState.UNKNOWN_STATE, event.getNewState());
    }

    @Test
    void serverIsOnlyAvailableWhenTheEventCarriesItsConfig() {
        ServerStateChangedEventImpl event = new ServerStateChangedEventImpl(ServerStateChangedEvent.newBuilder()
                .setServerId("server-1")
                .build());

        assertThrows(IllegalStateException.class, event::getServer);
    }
}
