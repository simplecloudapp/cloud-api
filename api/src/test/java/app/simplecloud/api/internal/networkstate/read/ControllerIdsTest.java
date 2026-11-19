package app.simplecloud.api.internal.networkstate.read;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControllerIdsTest {

    private static final ByteString ID = ByteString.fromHex("0d000000000040008000000000000a01");

    @Test
    void anIdIsWrittenInLowercaseGroups() {
        assertEquals("0d000000-0000-4000-8000-000000000a01", ControllerIds.text(ID));
    }

    @Test
    void anIdOfAnotherLengthIsRefused() {
        ByteString fifteenBytes = ID.substring(1);
        ByteString seventeenBytes = ID.concat(ByteString.fromHex("ff"));

        assertThrows(IllegalArgumentException.class, () -> ControllerIds.text(ByteString.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> ControllerIds.text(fifteenBytes));
        assertThrows(IllegalArgumentException.class, () -> ControllerIds.text(seventeenBytes));
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
