package app.simplecloud.api.player;

import app.simplecloud.api.group.Group;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectTargetTest {

    @Test
    void factories_rejectNull() {
        assertThrows(NullPointerException.class, () -> ConnectTarget.serverName(null));
        assertThrows(NullPointerException.class, () -> ConnectTarget.groupId(null, ServerSelectionMode.FIRST));
        assertThrows(NullPointerException.class, () -> ConnectTarget.group((Group) null));
        assertThrows(NullPointerException.class, () -> ConnectTarget.registeredServerName(null));
    }

    @Test
    void equals_comparesKindValueAndSelectionMode() {
        assertEquals(ConnectTarget.groupName("lobby"), ConnectTarget.groupName("lobby", null));
        assertNotEquals(ConnectTarget.groupName("lobby"), ConnectTarget.groupName("lobby", ServerSelectionMode.RANDOM));
        assertNotEquals(ConnectTarget.serverName("lobby"), ConnectTarget.registeredServerName("lobby"));
    }
}
