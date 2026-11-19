package app.simplecloud.api.internal.networkcopy;

import app.simplecloud.api.group.GroupQuery;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.web.ApiErrors;
import app.simplecloud.api.persistentserver.PersistentServerQuery;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsServerSummary;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The servers, groups and persistent servers of one network at one revision,
 * as the controller's list routes return them. Never changes.
 */
public final class NetworkContents {
    private static final Set<String> SORT_FIELDS = Set.of("created_at", "updated_at", "numerical_id", "state");

    private final long epoch;
    private final long revision;
    private final List<ModelsServerSummary> servers;
    private final List<ModelsServerGroupSummary> groups;
    private final List<ModelsPersistentServerSummary> persistentServers;
    private final Map<String, ModelsServerSummary> serversById = new HashMap<>();
    private final Map<String, ModelsServerGroupSummary> groupsById = new HashMap<>();
    private final Map<String, ModelsPersistentServerSummary> persistentServersById = new HashMap<>();

    /**
     * The lists must be in the controller's default order.
     */
    NetworkContents(long epoch,
                    long revision,
                    List<ModelsServerSummary> servers,
                    List<ModelsServerGroupSummary> groups,
                    List<ModelsPersistentServerSummary> persistentServers) {
        this.epoch = epoch;
        this.revision = revision;
        this.servers = List.copyOf(servers);
        this.groups = List.copyOf(groups);
        this.persistentServers = List.copyOf(persistentServers);
        for (ModelsServerSummary server : servers) {
            serversById.put(server.getServerId(), server);
        }
        for (ModelsServerGroupSummary group : groups) {
            groupsById.put(group.getServerGroupId(), group);
        }
        for (ModelsPersistentServerSummary persistentServer : persistentServers) {
            persistentServersById.put(persistentServer.getPersistentServerId(), persistentServer);
        }
    }

    public long epoch() {
        return epoch;
    }

    public long revision() {
        return revision;
    }

    /**
     * Exact match on the id text, as the HTTP path does today.
     */
    public @Nullable ModelsServerSummary serverById(String id) {
        return serversById.get(id);
    }

    /**
     * The servers that match, in the order the controller's list route returns them.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST}
     *                                               for an unknown sort field or a sort order other than asc or desc
     */
    public List<ModelsServerSummary> servers(@Nullable ServerQuery query) {
        if (query == null) {
            return servers;
        }
        List<ModelsServerSummary> matching = new ArrayList<>();
        for (ModelsServerSummary server : servers) {
            if (matches(server, query)) {
                matching.add(server);
            }
        }
        sortAsAsked(matching, query);
        return matching;
    }

    public @Nullable ModelsServerGroupSummary groupById(String id) {
        return groupsById.get(id);
    }

    /**
     * The first group with this name in list order.
     */
    public @Nullable ModelsServerGroupSummary groupByName(String name) {
        for (ModelsServerGroupSummary group : groups) {
            if (name.equals(group.getName())) {
                return group;
            }
        }
        return null;
    }

    /**
     * Filters by type, tag and limit. Sort field and sort order are ignored, as the HTTP path ignores them.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST} for a limit below 1
     */
    public List<ModelsServerGroupSummary> groups(@Nullable GroupQuery query) {
        if (query == null) {
            return groups;
        }
        List<ModelsServerGroupSummary> matching = new ArrayList<>();
        for (ModelsServerGroupSummary group : groups) {
            if (matches(group, query)) {
                matching.add(group);
            }
        }
        int keptCount = countUpToLimit(matching.size(), query.getLimit());
        return matching.subList(0, keptCount);
    }

    public @Nullable ModelsPersistentServerSummary persistentServerById(String id) {
        return persistentServersById.get(id);
    }

    /**
     * The first persistent server with this name in list order.
     */
    public @Nullable ModelsPersistentServerSummary persistentServerByName(String name) {
        for (ModelsPersistentServerSummary persistentServer : persistentServers) {
            if (name.equals(persistentServer.getName())) {
                return persistentServer;
            }
        }
        return null;
    }

    /**
     * Filters by tags, active, serverhost id and limit.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST} for a limit below 1
     */
    public List<ModelsPersistentServerSummary> persistentServers(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return persistentServers;
        }
        List<ModelsPersistentServerSummary> matching = new ArrayList<>();
        for (ModelsPersistentServerSummary persistentServer : persistentServers) {
            if (matches(persistentServer, query)) {
                matching.add(persistentServer);
            }
        }
        int keptCount = countUpToLimit(matching.size(), query.getLimit());
        return matching.subList(0, keptCount);
    }

    // A name, type or tag filter matches the group or the persistent server the server was started from.
    private static boolean matches(ModelsServerSummary server, ServerQuery query) {
        return isOneOfIds(server.getServerGroupId(), query.getServerGroupIds())
                && isOneOf(stateName(server), stateNames(query.getStates()))
                && isAskedId(server.getServerhostId(), query.getServerhostId())
                && isAskedId(server.getPersistentServerId(), query.getPersistentServerId())
                && isOneOf(baseType(server), typeNames(query.getServerGroupTypes()))
                && isOneOf(baseName(server), query.getServerGroupNames())
                && sharesOne(baseTags(server), query.getServerGroupTags())
                && isOneOf(server.getNumericalId(), query.getNumericalIds());
    }

    private static boolean matches(ModelsServerGroupSummary group, GroupQuery query) {
        GroupServerType askedType = query.getType();
        boolean typeMatches = askedType == null || askedType.name().equals(group.getType());
        String askedTag = query.getTag();
        boolean tagMatches = askedTag == null || askedTag.isBlank() || hasTag(group.getTags(), askedTag);
        return typeMatches && tagMatches;
    }

    private static boolean matches(ModelsPersistentServerSummary persistentServer, PersistentServerQuery query) {
        Boolean askedActive = query.getActive();
        boolean activeMatches = askedActive == null || askedActive.equals(persistentServer.getActive());
        return activeMatches
                && sharesOne(persistentServer.getTags(), query.getTags())
                && isAskedId(persistentServer.getServerhostId(), query.getServerhostId());
    }

    // A stable sort, so servers that compare equal keep the default order, also when descending.
    private static void sortAsAsked(List<ModelsServerSummary> servers, ServerQuery query) {
        String field = trimmed(query.getSortBy());
        String order = trimmed(query.getSortOrder());
        boolean knownField = field.isEmpty() || SORT_FIELDS.contains(field);
        if (!knownField) {
            throw ApiErrors.invalidRequest("invalid sort_by field; valid options: created_at, updated_at, numerical_id, state");
        }
        boolean descending = order.equals("desc");
        boolean knownOrder = order.isEmpty() || order.equals("asc") || descending;
        if (!knownOrder) {
            throw ApiErrors.invalidRequest("sort_order must be asc or desc");
        }
        if (field.isEmpty()) {
            return;
        }
        Comparator<ModelsServerSummary> ascending = (first, second) -> compareByField(field, first, second);
        Comparator<ModelsServerSummary> asked = descending ? ascending.reversed() : ascending;
        servers.sort(asked);
    }

    // Only the four sort fields reach this switch, so the last case is "state".
    private static int compareByField(String field, ModelsServerSummary first, ModelsServerSummary second) {
        return switch (field) {
            case "created_at" -> compareTimes(first.getCreatedAt(), second.getCreatedAt());
            case "updated_at" -> compareTimes(first.getUpdatedAt(), second.getUpdatedAt());
            case "numerical_id" -> Integer.compare(first.getNumericalId(), second.getNumericalId());
            default -> stateName(first).compareTo(stateName(second));
        };
    }

    // Parsed, because a text with fewer fraction digits can sort after one with more.
    private static int compareTimes(String first, String second) {
        Instant firstTime = Instant.parse(first);
        Instant secondTime = Instant.parse(second);
        return firstTime.compareTo(secondTime);
    }

    private static int countUpToLimit(int matchingCount, @Nullable Integer limit) {
        if (limit == null) {
            return matchingCount;
        }
        if (limit < 1) {
            throw ApiErrors.invalidRequest("limit must be at least 1");
        }
        return Math.min(limit, matchingCount);
    }

    private static String trimmed(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.strip();
    }

    private static String stateName(ModelsServerSummary server) {
        return server.getState().getValue();
    }

    private static @Nullable List<String> stateNames(@Nullable List<ServerState> states) {
        if (states == null) {
            return null;
        }
        List<String> names = new ArrayList<>(states.size());
        for (ServerState state : states) {
            names.add(state.name());
        }
        return names;
    }

    private static @Nullable List<String> typeNames(@Nullable List<GroupServerType> types) {
        if (types == null) {
            return null;
        }
        List<String> names = new ArrayList<>(types.size());
        for (GroupServerType type : types) {
            names.add(type.name());
        }
        return names;
    }

    private static @Nullable String baseName(ModelsServerSummary server) {
        if (server.getServerGroup() != null) {
            return server.getServerGroup().getName();
        }
        if (server.getPersistentServer() != null) {
            return server.getPersistentServer().getName();
        }
        return null;
    }

    private static @Nullable String baseType(ModelsServerSummary server) {
        if (server.getServerGroup() != null) {
            return server.getServerGroup().getType();
        }
        if (server.getPersistentServer() != null) {
            return server.getPersistentServer().getType();
        }
        return null;
    }

    private static @Nullable List<String> baseTags(ModelsServerSummary server) {
        if (server.getServerGroup() != null) {
            return server.getServerGroup().getTags();
        }
        if (server.getPersistentServer() != null) {
            return server.getPersistentServer().getTags();
        }
        return null;
    }

    private static boolean isOneOf(@Nullable Object value, @Nullable List<?> asked) {
        if (asked == null || asked.isEmpty()) {
            return true;
        }
        return asked.contains(value);
    }

    private static boolean isOneOfIds(@Nullable String id, @Nullable List<String> askedIds) {
        if (askedIds == null || askedIds.isEmpty()) {
            return true;
        }
        for (String askedId : askedIds) {
            if (askedId.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAskedId(@Nullable String id, @Nullable String askedId) {
        if (askedId == null || askedId.isBlank()) {
            return true;
        }
        return askedId.equalsIgnoreCase(id);
    }

    private static boolean sharesOne(@Nullable List<String> tags, @Nullable List<String> askedTags) {
        if (askedTags == null || askedTags.isEmpty()) {
            return true;
        }
        for (String askedTag : askedTags) {
            if (hasTag(tags, askedTag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTag(@Nullable List<String> tags, String tag) {
        return tags != null && tags.contains(tag);
    }
}
