package app.simplecloud.api.server;

import app.simplecloud.api.group.GroupServerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerQueryTest {

    @Test
    void rejectsUnknownSentinels() {
        ServerQuery query = ServerQuery.create();

        assertThrows(IllegalArgumentException.class, () -> query.filterByState(ServerState.UNKNOWN_STATE));
        assertThrows(
                IllegalArgumentException.class, () -> query.filterByServerGroupType(GroupServerType.UNKNOWN_SERVER));
    }

    @Test
    void rejectsNullFilterValues() {
        ServerQuery query = ServerQuery.create();

        assertThrows(NullPointerException.class, () -> query.filterByServerGroupName("lobby", null));
        assertThrows(NullPointerException.class, () -> query.filterByNumericalId(1, null));
    }

    @Test
    void repeatedCallsAddValues() {
        ServerQuery query = ServerQuery.create().filterByServerhostId("host-1").filterByServerhostId("host-2");

        assertEquals(List.of("host-1", "host-2"), query.getServerhostIds());
    }
}
