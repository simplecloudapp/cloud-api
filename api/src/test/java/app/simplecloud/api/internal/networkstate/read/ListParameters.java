package app.simplecloud.api.internal.networkstate.read;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Route parameters for the tests, either none or read from a case of {@code list-cases.json}, whose keys are
 * the query parameter names.
 */
final class ListParameters {

    private ListParameters() {
    }

    static ServerListParameters servers() {
        return servers(new JsonObject());
    }

    static ServerGroupListParameters serverGroups() {
        return serverGroups(new JsonObject());
    }

    static PersistentServerListParameters persistentServers() {
        return persistentServers(new JsonObject());
    }

    static ServerListParameters servers(JsonObject query) {
        Reader reader = new Reader(query);
        ServerListParameters parameters = new ServerListParameters(
                reader.text("server_id"),
                reader.text("server_group_id"),
                reader.text("state"),
                reader.text("serverhost_id"),
                reader.text("persistent_server_id"),
                reader.text("type"),
                reader.text("name"),
                reader.text("tags"),
                reader.text("numerical_id"),
                reader.text("sort_by"),
                reader.text("sort_order"),
                reader.number("limit"),
                reader.number("offset"));
        reader.requireAllRead();
        return parameters;
    }

    static ServerGroupListParameters serverGroups(JsonObject query) {
        Reader reader = new Reader(query);
        ServerGroupListParameters parameters = new ServerGroupListParameters(
                reader.text("server_group_id"),
                reader.text("name"),
                reader.text("type"),
                reader.text("tags"),
                reader.number("limit"),
                reader.number("offset"));
        reader.requireAllRead();
        return parameters;
    }

    static PersistentServerListParameters persistentServers(JsonObject query) {
        Reader reader = new Reader(query);
        PersistentServerListParameters parameters = new PersistentServerListParameters(
                reader.text("persistent_server_id"),
                reader.text("name"),
                reader.text("tags"),
                reader.bool("active"),
                reader.text("serverhost_id"),
                reader.number("limit"),
                reader.number("offset"));
        reader.requireAllRead();
        return parameters;
    }

    /**
     * Fails a case that names a parameter its route's record does not have, which would otherwise test
     * nothing.
     */
    private static final class Reader {

        private final JsonObject query;
        private final Set<String> read = new HashSet<>();

        Reader(JsonObject query) {
            this.query = query;
        }

        String text(String name) {
            JsonElement value = take(name);
            return value == null ? null : value.getAsString();
        }

        Integer number(String name) {
            JsonElement value = take(name);
            return value == null ? null : value.getAsInt();
        }

        Boolean bool(String name) {
            JsonElement value = take(name);
            return value == null ? null : value.getAsBoolean();
        }

        void requireAllRead() {
            for (Map.Entry<String, JsonElement> parameter : query.entrySet()) {
                if (!read.contains(parameter.getKey())) {
                    throw new IllegalArgumentException("The route has no parameter " + parameter.getKey());
                }
            }
        }

        private JsonElement take(String name) {
            read.add(name);
            return query.get(name);
        }
    }
}
