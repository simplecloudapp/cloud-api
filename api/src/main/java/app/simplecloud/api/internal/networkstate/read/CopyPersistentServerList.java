package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.internal.networkstate.ReadKind;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import build.buf.gen.simplecloud.controller.v2.StatePersistentServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Answers {@code GET /v0/persistent-servers} from a copy as the controller would at the copy's revision:
 * {@code handleListPersistentServers} in {@code internal/web/persistent_servers.go} and
 * {@code ListPersistentServersWithFilters} in {@code db/queries/persistent_servers.sql}, for a caller that
 * may read every persistent server.
 */
public final class CopyPersistentServerList
        implements CopyList<PersistentServerListParameters, ModelsListPersistentServersResponse> {

    /**
     * {@code ORDER BY created_at DESC, id}.
     */
    private static final Comparator<ListedPersistentServer> ORDER = Comparator
            .comparing(ListedPersistentServer::createdAt, Comparator.reverseOrder())
            .thenComparing(ListedPersistentServer::id, ByteOrder.ID);

    @Override
    public ReadKind kind() {
        return ReadKind.PERSISTENT_SERVER;
    }

    /**
     * Returns the controller's answer, read as the HTTP client reads it, or empty when only the controller can
     * give it: it refuses the parameters with an error, or the request picks persistent servers by id or name
     * and the copy has none of them, since such a persistent server may be newer than the copy. A body the
     * HTTP client cannot read throws as it does over HTTP.
     */
    @Override
    public Optional<ModelsListPersistentServersResponse> answer(
            NetworkCopy copy,
            PersistentServerListParameters parameters
    ) {
        Optional<JsonObject> body = responseBody(copy, parameters);
        return body.map(json -> ControllerJson.toModel(json, ModelsListPersistentServersResponse.class));
    }

    @Override
    public boolean refusesParameters(PersistentServerListParameters parameters) {
        return Query.parse(parameters).isEmpty();
    }

    /**
     * Returns the body the controller would send, under the conditions of {@link #answer}.
     */
    static Optional<JsonObject> responseBody(NetworkCopy copy, PersistentServerListParameters parameters) {
        Optional<Query> parsed = Query.parse(parameters);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Query query = parsed.get();
        List<ListedPersistentServer> matching = new ArrayList<>();
        for (StatePersistentServer persistentServer : copy.persistentServers().values()) {
            ListedPersistentServer listed = ListedPersistentServer.of(persistentServer);
            if (query.matches(listed)) {
                matching.add(listed);
            }
        }
        matching.sort(ORDER);
        List<ListedPersistentServer> page = query.page().select(matching);
        if (query.picksPersistentServers() && page.isEmpty()) {
            return Optional.empty();
        }
        // Unlike the other two lists, the handler makes an empty slice here, which Go encodes as [].
        JsonArray persistentServers = new JsonArray();
        // Only the page: the player count of each persistent server scans every server.
        for (ListedPersistentServer listed : page) {
            persistentServers.add(PersistentServerJson.render(listed.persistentServer(), copy));
        }
        JsonObject body = new JsonObject();
        body.add("persistent_servers", persistentServers);
        body.addProperty("count", persistentServers.size());
        return Optional.of(body);
    }

    /**
     * A persistent server with the members the filters and the order read, from its summary, which the
     * rendered persistent server keeps as it is. Only the listed ones are read into models, as over HTTP.
     */
    private record ListedPersistentServer(
            StatePersistentServer persistentServer,
            @Nullable String name,
            @Nullable List<String> tags,
            @Nullable Boolean active,
            @Nullable String serverhostId,
            Instant createdAt
    ) {

        static ListedPersistentServer of(StatePersistentServer persistentServer) {
            JsonObject summary = ControllerJson.parseObject(persistentServer.getSummaryJson());
            Instant createdAt = Instant.parse(ControllerJson.text(summary, "created_at"));
            return new ListedPersistentServer(persistentServer, ControllerJson.text(summary, "name"),
                    ControllerJson.texts(summary, "tags"), ControllerJson.bool(summary, "active"),
                    ControllerJson.text(summary, "serverhost_id"), createdAt);
        }

        ByteString id() {
            return persistentServer.getId();
        }
    }

    /**
     * The parameters as {@code parsePersistentServerListFilters} in {@code internal/web/list_filters.go}
     * reads them. An empty list does not filter.
     *
     * @param serverhostIds formatted as the controller formats a {@code serverhost_id}, so equal ids are equal
     *                      text
     */
    private record Query(
            List<ByteString> ids,
            List<String> names,
            List<String> tags,
            @Nullable Boolean active,
            List<String> serverhostIds,
            Page page
    ) {

        static Optional<Query> parse(PersistentServerListParameters parameters) {
            Optional<List<ByteString>> ids = ControllerQueryValues.parseUuids(
                    ControllerQueryValues.splitCsv(parameters.persistentServerId()));
            List<String> names = ControllerQueryValues.splitCsv(parameters.name());
            List<String> tags = ControllerQueryValues.splitCsv(parameters.tags());
            Optional<List<ByteString>> serverhostIds = ControllerQueryValues.parseUuids(
                    ControllerQueryValues.splitCsv(parameters.serverhostId()));
            Optional<Page> page = Page.parse(parameters.limit(), parameters.offset());

            boolean textAccepted = names.stream().noneMatch(ControllerQueryValues::isRejectedAsText)
                    && tags.stream().noneMatch(ControllerQueryValues::isRejectedAsText);
            boolean accepted = ids.isPresent() && serverhostIds.isPresent() && textAccepted && page.isPresent();
            if (!accepted) {
                return Optional.empty();
            }
            List<String> formattedServerhostIds = serverhostIds.get().stream().map(ControllerJson::uuid).toList();
            return Optional.of(new Query(ids.get(), names, tags, parameters.active(), formattedServerhostIds,
                    page.get()));
        }

        boolean picksPersistentServers() {
            return !ids.isEmpty() || !names.isEmpty();
        }

        /**
         * Tags match as Postgres's {@code &&} does: one tag in common is enough.
         */
        boolean matches(ListedPersistentServer persistentServer) {
            boolean idMatches = ids.isEmpty() || ids.contains(persistentServer.id());
            boolean nameMatches = names.isEmpty() || names.contains(persistentServer.name());
            boolean tagsMatch = tags.isEmpty()
                    || (persistentServer.tags() != null && !Collections.disjoint(tags, persistentServer.tags()));
            boolean activeMatches = active == null || active.equals(persistentServer.active());
            boolean serverhostMatches = serverhostIds.isEmpty()
                    || serverhostIds.contains(persistentServer.serverhostId());
            return idMatches && nameMatches && tagsMatch && activeMatches && serverhostMatches;
        }
    }
}
