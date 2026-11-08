package app.simplecloud.api.internal.util;

import app.simplecloud.api.server.ServerState;
import build.buf.gen.simplecloud.controller.v2.ServerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProtoConversionUtilTest {

    @Test
    void enumNamesLoseTheirPrefix() {
        assertEquals("SERVER", ProtoConversionUtil.convertServerTypeToString(ServerType.SERVER_TYPE_SERVER));
        assertEquals(
                ServerState.AVAILABLE,
                ProtoConversionUtil.convertServerState(
                        build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_AVAILABLE));
    }

    @Test
    void unsetAndUnknownEnumsBecomeNull() {
        assertNull(ProtoConversionUtil.convertServerTypeToString(ServerType.SERVER_TYPE_UNSPECIFIED));
        assertNull(ProtoConversionUtil.convertServerTypeToString(ServerType.UNRECOGNIZED));
        assertNull(ProtoConversionUtil.convertServerState(
                build.buf.gen.simplecloud.controller.v2.ServerState.SERVER_STATE_UNSPECIFIED));
        assertNull(ProtoConversionUtil.convertServerState(null));
    }

    @Test
    void optionalTimestampsAreNullWhenUnset() {
        assertNull(ProtoConversionUtil.optionalEpochMillisToIso(0));
        assertEquals("2023-11-14T22:13:20Z", ProtoConversionUtil.optionalEpochMillisToIso(1_700_000_000_000L));
        assertEquals("2023-11-14T22:13:20Z", ProtoConversionUtil.epochSecondsToIso(1_700_000_000L));
    }

    @Test
    void joinOrNullJoinsValuesAndEnumNames() {
        assertNull(QueryParams.joinOrNull(null));
        assertNull(QueryParams.joinOrNull(List.of()));
        assertEquals("1,2", QueryParams.joinOrNull(List.of(1, 2)));
        assertEquals("AVAILABLE,INGAME", QueryParams.joinOrNull(List.of(ServerState.AVAILABLE, ServerState.INGAME)));
    }
}
