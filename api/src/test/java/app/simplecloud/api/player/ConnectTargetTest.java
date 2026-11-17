package app.simplecloud.api.player;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.internal.player.ConnectTargetFields;
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

    @Test
    void registerReader_afterConnectTargetRegisteredItsReader_throws() {
        ConnectTarget.serverName("lobby-1");
        ConnectTargetFields.Reader otherReader = target -> new ConnectTargetFields(
                ConnectTargetFields.Kind.SERVER_NAME, "other", null
        );

        assertThrows(IllegalStateException.class, () -> ConnectTargetFields.registerReader(otherReader));
    }
}
