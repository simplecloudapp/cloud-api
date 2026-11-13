package app.simplecloud.api.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryKeyTest {

    @Test
    void matches_isTrueForItselfAndLongerKeysWithSamePrefix() {
        QueryKey servers = QueryKey.of("servers");

        assertTrue(servers.matches(QueryKey.of("servers")));
        assertTrue(servers.matches(QueryKey.of("servers", "group", "Lobby")));
    }

    @Test
    void matches_isFalseForShorterOrDifferentKeys() {
        QueryKey lobbyServers = QueryKey.of("servers", "group", "Lobby");

        assertFalse(lobbyServers.matches(QueryKey.of("servers")));
        assertFalse(lobbyServers.matches(QueryKey.of("servers", "group", "Game")));
        assertFalse(QueryKey.of("server").matches(QueryKey.of("servers")));
    }

    @Test
    void equalKeys_haveEqualHashCodes() {
        QueryKey first = QueryKey.of("server", "numerical", "Lobby", 1);
        QueryKey second = QueryKey.of("server", "numerical", "Lobby", 1);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, QueryKey.of("server", "numerical", "Lobby", 2));
    }

    @Test
    void nestedArrayParts_compareByContent() {
        QueryKey first = QueryKey.of("servers", "query", new Object[]{"Lobby", 1});
        QueryKey second = QueryKey.of("servers", "query", new Object[]{"Lobby", 1});

        assertEquals(first, second);
    }

    /**
     * Regression test: the key used to keep the caller's array, so changing that array
     * afterwards changed the key while its cached hash code stayed the same.
     */
    @Test
    void of_copiesCallerArray() {
        Object[] parts = {"server", "server-1"};
        QueryKey key = QueryKey.of(parts);

        parts[1] = "server-2";

        assertEquals(QueryKey.of("server", "server-1"), key);
    }

    @Test
    void getParts_returnsCopy() {
        QueryKey key = QueryKey.of("server", "server-1");

        key.getParts()[1] = "server-2";

        assertEquals(QueryKey.of("server", "server-1"), key);
    }

    @ParameterizedTest
    @CsvSource({
            "server, SERVER",
            "servers, SERVER",
            "group, GROUP",
            "groups, GROUP",
            "persistentServer, PERSISTENT_SERVER",
            "persistentServers, PERSISTENT_SERVER",
            "player, PLAYER",
            "players, PLAYER"
    })
    void getEntityType_readsFirstPart(String firstPart, EntityType expected) {
        QueryKey key = QueryKey.of(firstPart, "id");

        assertEquals(expected, key.getEntityType());
    }

    /**
     * Regression test: a null first part used to throw, and fetch() calls getEntityType() on every key.
     */
    @Test
    void getEntityType_isNullForUnknownEmptyOrNullFirstPart() {
        assertNull(QueryKey.of("blueprint", "id").getEntityType());
        assertNull(QueryKey.of().getEntityType());
        assertNull(QueryKey.of(null, "id").getEntityType());
    }

    @Test
    void matches_comparesArrayPartsByContent() {
        QueryKey pattern = QueryKey.of("servers", new Object[]{"Lobby"});
        QueryKey key = QueryKey.of("servers", new Object[]{"Lobby"}, "page-1");

        assertTrue(pattern.matches(key), "matches() must agree with equals() on array parts");
    }

    private enum KeyPrefix {
        server
    }

    @Test
    void getEntityType_readsNonStringFirstPartThroughToString() {
        QueryKey key = QueryKey.of(KeyPrefix.server, "id");

        assertEquals(EntityType.SERVER, key.getEntityType());
    }
}
