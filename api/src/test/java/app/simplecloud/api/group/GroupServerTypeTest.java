package app.simplecloud.api.group;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupServerTypeTest {

    @Test
    void parseIgnoresCase() {
        assertEquals(GroupServerType.SERVER, GroupServerType.parse("SERVER"));
        assertEquals(GroupServerType.PROXY, GroupServerType.parse("proxy"));
    }

    @Test
    void parseFallsBackToUnknownForMissingOrNewTypes() {
        assertEquals(GroupServerType.UNKNOWN_SERVER, GroupServerType.parse(null));
        assertEquals(GroupServerType.UNKNOWN_SERVER, GroupServerType.parse(""));
        assertEquals(GroupServerType.UNKNOWN_SERVER, GroupServerType.parse("LOBBY"));
    }
}
