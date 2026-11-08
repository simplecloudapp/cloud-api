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
        ServerQuery query =
                ServerQuery.create().filterByServerhostId("host-1", "host-2").filterByServerhostId("host-3", "host-4");

        assertEquals(List.of("host-1", "host-2", "host-3", "host-4"), query.getServerhostIds());
    }

    @Test
    void singleValueCallReplacesValues() {
        ServerQuery query =
                ServerQuery.create().filterByServerhostId("host-1", "host-2").filterByServerhostId("host-3");

        assertEquals(List.of("host-3"), query.getServerhostIds());
    }
}
