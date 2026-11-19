package app.simplecloud.api.internal.networkstate.read;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ControllerIdsTest {

    private static final ByteString ID = ByteString.fromHex("0d000000000040008000000000000a01");

    @Test
    void anIdIsWrittenInLowercaseGroups() {
        assertEquals("0d000000-0000-4000-8000-000000000a01", ControllerIds.text(ID));
    }

    @Test
    void anIdIsReadInEitherCase() {
        assertEquals(ID, ControllerIds.bytes("0D000000-0000-4000-8000-000000000A01"));
    }

    @Test
    void anIdInAnotherFormIsNoId() {
        assertNull(ControllerIds.bytes("0d000000000040008000000000000a01"));
        assertNull(ControllerIds.bytes("lobby"));
        assertNull(ControllerIds.bytes(null));
    }
}
