package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.internal.networkstate.NetworkCopy;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import build.buf.gen.simplecloud.controller.v2.StateServerGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Answers {@code GET /v0/server-groups} from a copy as the controller would at the copy's revision:
 * {@code handleListServerGroups} in {@code internal/web/server_groups.go} and
 * {@code ListServerGroupsWithFilters} in {@code db/queries/server_groups.sql}, for a caller that may read
 * every group.
 */
public final class CopyServerGroupList implements CopyList<ServerGroupListParameters, ModelsListServerGroupsResponse> {

    private static final Set<String> TYPES = Set.of("SERVER", "PROXY");

    /**
     * {@code ORDER BY created_at DESC, id}.
     */
    private static final Comparator<ListedGroup> ORDER = Comparator
            .comparing(ListedGroup::createdAt, Comparator.reverseOrder())
            .thenComparing(ListedGroup::id, ByteOrder.ID);

    /**
     * Returns the controller's answer, read as the HTTP client reads it, or empty when only the controller can
     * give it: it refuses the parameters with an error, or the request picks groups by id or name and the copy
     * has none of them, since such a group may be newer than the copy. A body the HTTP client cannot read
     * throws as it does over HTTP.
     */
    @Override
    public Optional<ModelsListServerGroupsResponse> answer(NetworkCopy copy, ServerGroupListParameters parameters) {
        Optional<JsonObject> body = responseBody(copy, parameters);
        return body.map(json -> ControllerJson.toModel(json, ModelsListServerGroupsResponse.class));
    }

    @Override
    public boolean refuses(ServerGroupListParameters parameters) {
        return Query.parse(parameters).isEmpty();
    }

    /**
     * Returns the body the controller would send, under the conditions of {@link #answer}.
     */
    static Optional<JsonObject> responseBody(NetworkCopy copy, ServerGroupListParameters parameters) {
        Optional<Query> parsed = Query.parse(parameters);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Query query = parsed.get();
        List<ListedGroup> matching = new ArrayList<>();
        for (StateServerGroup group : copy.serverGroups().values()) {
            ListedGroup listed = ListedGroup.of(group);
            if (query.matches(listed)) {
                matching.add(listed);
            }
        }
        matching.sort(ORDER);
        List<ListedGroup> page = query.page().select(matching);
        if (query.picksGroups() && page.isEmpty()) {
            return Optional.empty();
        }
        // Only the page: the stats of each group scan every server.
        JsonArray groups = new JsonArray();
        for (ListedGroup listed : page) {
            groups.add(ServerGroupJson.render(listed.group(), copy));
        }
        JsonObject body = new JsonObject();
        // The handler collects the groups into a nil slice, which Go encodes as null when nothing matched.
        body.add("server_groups", groups.isEmpty() ? JsonNull.INSTANCE : groups);
        body.addProperty("count", groups.size());
        return Optional.of(body);
    }

    /**
     * A group with the members the filters and the order read, from its summary, which the rendered group
     * keeps as it is. Only the listed groups are read into models, as over HTTP, so a group the client cannot
     * read fails only a list that shows it.
     */
    private record ListedGroup(
            StateServerGroup group,
            @Nullable String name,
            @Nullable String type,
            @Nullable List<String> tags,
            Instant createdAt
    ) {

        static ListedGroup of(StateServerGroup group) {
            JsonObject summary = ControllerJson.parseObject(group.getSummaryJson());
            Instant createdAt = Instant.parse(ControllerJson.text(summary, "created_at"));
            return new ListedGroup(group, ControllerJson.text(summary, "name"),
                    ControllerJson.text(summary, "type"), ControllerJson.texts(summary, "tags"), createdAt);
        }

        ByteString id() {
            return group.getId();
        }
    }

    /**
     * The parameters as {@code parseServerGroupListFilters} in {@code internal/web/list_filters.go} reads
     * them. An empty list does not filter.
     */
    private record Query(List<ByteString> ids, List<String> names, List<String> types, List<String> tags, Page page) {

        static Optional<Query> parse(ServerGroupListParameters parameters) {
            Optional<List<ByteString>> ids = ControllerQueryValues.parseUuids(
                    ControllerQueryValues.splitCsv(parameters.serverGroupId()));
            List<String> names = ControllerQueryValues.splitCsv(parameters.name());
            List<String> types = ControllerQueryValues.splitCsv(parameters.type());
            List<String> tags = ControllerQueryValues.splitCsv(parameters.tags());
            Optional<Page> page = Page.parse(parameters.limit(), parameters.offset());

            boolean textAccepted = names.stream().noneMatch(ControllerQueryValues::isRejectedAsText)
                    && tags.stream().noneMatch(ControllerQueryValues::isRejectedAsText);
            boolean accepted = ids.isPresent() && TYPES.containsAll(types) && textAccepted && page.isPresent();
            if (!accepted) {
                return Optional.empty();
            }
            return Optional.of(new Query(ids.get(), names, types, tags, page.get()));
        }

        boolean picksGroups() {
            return !ids.isEmpty() || !names.isEmpty();
        }

        /**
         * Tags match as Postgres's {@code &&} does: one tag in common is enough.
         */
        boolean matches(ListedGroup group) {
            boolean idMatches = ids.isEmpty() || ids.contains(group.id());
            boolean nameMatches = names.isEmpty() || names.contains(group.name());
            boolean typeMatches = types.isEmpty() || types.contains(group.type());
            boolean tagsMatch = tags.isEmpty() || (group.tags() != null && !Collections.disjoint(tags, group.tags()));
            return idMatches && nameMatches && typeMatches && tagsMatch;
        }
    }
}
