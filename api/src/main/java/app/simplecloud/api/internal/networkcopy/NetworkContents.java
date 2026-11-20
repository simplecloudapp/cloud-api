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
    private static final Set<String> SERVER_SORT_FIELDS = Set.of("created_at", "updated_at", "numerical_id", "state");

    private final long epoch;
    private final long revision;
    private final List<ModelsServerSummary> serverSummaries;
    private final List<ModelsServerGroupSummary> groupSummaries;
    private final List<ModelsPersistentServerSummary> persistentServerSummaries;
    private final Map<String, ModelsServerSummary> serverSummariesById = new HashMap<>();
    private final Map<String, ModelsServerGroupSummary> groupSummariesById = new HashMap<>();
    private final Map<String, ModelsPersistentServerSummary> persistentServerSummariesById = new HashMap<>();

    /**
     * The lists must be in the controller's default order.
     */
    NetworkContents(long epoch,
                    long revision,
                    List<ModelsServerSummary> serverSummaries,
                    List<ModelsServerGroupSummary> groupSummaries,
                    List<ModelsPersistentServerSummary> persistentServerSummaries) {
        this.epoch = epoch;
        this.revision = revision;
        this.serverSummaries = List.copyOf(serverSummaries);
        this.groupSummaries = List.copyOf(groupSummaries);
        this.persistentServerSummaries = List.copyOf(persistentServerSummaries);
        for (ModelsServerSummary serverSummary : serverSummaries) {
            serverSummariesById.put(serverSummary.getServerId(), serverSummary);
        }
        for (ModelsServerGroupSummary groupSummary : groupSummaries) {
            groupSummariesById.put(groupSummary.getServerGroupId(), groupSummary);
        }
        for (ModelsPersistentServerSummary persistentServerSummary : persistentServerSummaries) {
            persistentServerSummariesById.put(persistentServerSummary.getPersistentServerId(), persistentServerSummary);
        }
    }

    public long epoch() {
        return epoch;
    }

    public long revision() {
        return revision;
    }

    /**
     * Exact match on the id text, as the HTTP path does.
     */
    public @Nullable ModelsServerSummary findServerSummaryById(String id) {
        return serverSummariesById.get(id);
    }

    /**
     * The servers that match, in the order the controller's list route returns them.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST}
     *                                               for an unknown sort field or a sort order other than asc or desc
     */
    public List<ModelsServerSummary> listServerSummaries(@Nullable ServerQuery query) {
        if (query == null) {
            return serverSummaries;
        }
        String sortField = stripOrEmpty(query.getSortBy());
        String sortOrder = stripOrEmpty(query.getSortOrder());
        refuseUnknownSort(sortField, sortOrder);
        List<ModelsServerSummary> matching = new ArrayList<>();
        for (ModelsServerSummary serverSummary : serverSummaries) {
            if (matches(serverSummary, query)) {
                matching.add(serverSummary);
            }
        }
        sortAsAsked(matching, sortField, sortOrder);
        return List.copyOf(matching);
    }

    public @Nullable ModelsServerGroupSummary findGroupSummaryById(String id) {
        return groupSummariesById.get(id);
    }

    /**
     * The first group with this name in list order.
     */
    public @Nullable ModelsServerGroupSummary findGroupSummaryByName(String name) {
        for (ModelsServerGroupSummary groupSummary : groupSummaries) {
            if (name.equals(groupSummary.getName())) {
                return groupSummary;
            }
        }
        return null;
    }

    /**
     * Filters by type, tag and limit. Sort field and sort order are ignored, as the HTTP path ignores them.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST} for a limit below 1
     */
    public List<ModelsServerGroupSummary> listGroupSummaries(@Nullable GroupQuery query) {
        if (query == null) {
            return groupSummaries;
        }
        refuseLimitBelowOne(query.getLimit());
        List<ModelsServerGroupSummary> matching = new ArrayList<>();
        for (ModelsServerGroupSummary groupSummary : groupSummaries) {
            if (matches(groupSummary, query)) {
                matching.add(groupSummary);
            }
        }
        int keptCount = countUpToLimit(matching.size(), query.getLimit());
        List<ModelsServerGroupSummary> kept = matching.subList(0, keptCount);
        return List.copyOf(kept);
    }

    public @Nullable ModelsPersistentServerSummary findPersistentServerSummaryById(String id) {
        return persistentServerSummariesById.get(id);
    }

    /**
     * The first persistent server with this name in list order.
     */
    public @Nullable ModelsPersistentServerSummary findPersistentServerSummaryByName(String name) {
        for (ModelsPersistentServerSummary persistentServerSummary : persistentServerSummaries) {
            if (name.equals(persistentServerSummary.getName())) {
                return persistentServerSummary;
            }
        }
        return null;
    }

    /**
     * Filters by tags, active, serverhost id and limit.
     *
     * @throws app.simplecloud.api.CloudApiException with reason {@code INVALID_REQUEST} for a limit below 1
     */
    public List<ModelsPersistentServerSummary> listPersistentServerSummaries(@Nullable PersistentServerQuery query) {
        if (query == null) {
            return persistentServerSummaries;
        }
        refuseLimitBelowOne(query.getLimit());
        List<ModelsPersistentServerSummary> matching = new ArrayList<>();
        for (ModelsPersistentServerSummary persistentServerSummary : persistentServerSummaries) {
            if (matches(persistentServerSummary, query)) {
                matching.add(persistentServerSummary);
            }
        }
        int keptCount = countUpToLimit(matching.size(), query.getLimit());
        List<ModelsPersistentServerSummary> kept = matching.subList(0, keptCount);
        return List.copyOf(kept);
    }

    // A name, type or tag filter matches the group or the persistent server the server was started from.
    private static boolean matches(ModelsServerSummary serverSummary, ServerQuery query) {
        return passesIdListFilter(serverSummary.getServerGroupId(), query.getServerGroupIds())
                && passesValueFilter(getStateName(serverSummary), toStateNames(query.getStates()))
                && passesIdFilter(serverSummary.getServerhostId(), query.getServerhostId())
                && passesIdFilter(serverSummary.getPersistentServerId(), query.getPersistentServerId())
                && passesValueFilter(findGroupOrPersistentServerType(serverSummary), toTypeNames(query.getServerGroupTypes()))
                && passesValueFilter(findGroupOrPersistentServerName(serverSummary), query.getServerGroupNames())
                && passesTagFilter(findGroupOrPersistentServerTags(serverSummary), query.getServerGroupTags())
                && passesValueFilter(serverSummary.getNumericalId(), query.getNumericalIds());
    }

    private static boolean matches(ModelsServerGroupSummary groupSummary, GroupQuery query) {
        GroupServerType askedType = query.getType();
        boolean typeMatches = askedType == null || askedType.name().equals(groupSummary.getType());
        String askedTag = query.getTag();
        boolean tagMatches = askedTag == null || askedTag.isBlank() || hasTag(groupSummary.getTags(), askedTag);
        return typeMatches && tagMatches;
    }

    private static boolean matches(ModelsPersistentServerSummary persistentServerSummary, PersistentServerQuery query) {
        Boolean askedActive = query.getActive();
        boolean activeMatches = askedActive == null || askedActive.equals(persistentServerSummary.getActive());
        return activeMatches
                && passesTagFilter(persistentServerSummary.getTags(), query.getTags())
                && passesIdFilter(persistentServerSummary.getServerhostId(), query.getServerhostId());
    }

    private static void refuseUnknownSort(String sortField, String sortOrder) {
        boolean knownField = sortField.isEmpty() || SERVER_SORT_FIELDS.contains(sortField);
        if (!knownField) {
            throw ApiErrors.invalidRequest("invalid sort_by field; valid options: created_at, updated_at, numerical_id, state");
        }
        boolean knownOrder = sortOrder.isEmpty() || sortOrder.equals("asc") || sortOrder.equals("desc");
        if (!knownOrder) {
            throw ApiErrors.invalidRequest("sort_order must be asc or desc");
        }
    }

    private static void refuseLimitBelowOne(@Nullable Integer limit) {
        if (limit != null && limit < 1) {
            throw ApiErrors.invalidRequest("limit must be at least 1");
        }
    }

    // A stable sort, so servers that compare equal keep the default order, also when descending.
    private static void sortAsAsked(List<ModelsServerSummary> serverSummaries, String sortField, String sortOrder) {
        if (sortField.isEmpty()) {
            return;
        }
        boolean descending = sortOrder.equals("desc");
        Comparator<ModelsServerSummary> ascendingComparator = (first, second) -> compareByField(sortField, first, second);
        Comparator<ModelsServerSummary> askedComparator = descending ? ascendingComparator.reversed() : ascendingComparator;
        serverSummaries.sort(askedComparator);
    }

    private static int compareByField(String field, ModelsServerSummary first, ModelsServerSummary second) {
        return switch (field) {
            case "created_at" -> compareTimes(first.getCreatedAt(), second.getCreatedAt());
            case "updated_at" -> compareTimes(first.getUpdatedAt(), second.getUpdatedAt());
            case "numerical_id" -> Integer.compare(first.getNumericalId(), second.getNumericalId());
            case "state" -> getStateName(first).compareTo(getStateName(second));
            default -> throw new IllegalStateException("unchecked sort field " + field);
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
        return Math.min(limit, matchingCount);
    }

    private static String stripOrEmpty(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.strip();
    }

    private static String getStateName(ModelsServerSummary serverSummary) {
        return serverSummary.getState().getValue();
    }

    private static @Nullable List<String> toStateNames(@Nullable List<ServerState> states) {
        if (states == null) {
            return null;
        }
        List<String> names = new ArrayList<>(states.size());
        for (ServerState state : states) {
            names.add(state.name());
        }
        return names;
    }

    private static @Nullable List<String> toTypeNames(@Nullable List<GroupServerType> types) {
        if (types == null) {
            return null;
        }
        List<String> names = new ArrayList<>(types.size());
        for (GroupServerType type : types) {
            names.add(type.name());
        }
        return names;
    }

    private static @Nullable String findGroupOrPersistentServerName(ModelsServerSummary serverSummary) {
        if (serverSummary.getServerGroup() != null) {
            return serverSummary.getServerGroup().getName();
        }
        if (serverSummary.getPersistentServer() != null) {
            return serverSummary.getPersistentServer().getName();
        }
        return null;
    }

    private static @Nullable String findGroupOrPersistentServerType(ModelsServerSummary serverSummary) {
        if (serverSummary.getServerGroup() != null) {
            return serverSummary.getServerGroup().getType();
        }
        if (serverSummary.getPersistentServer() != null) {
            return serverSummary.getPersistentServer().getType();
        }
        return null;
    }

    private static @Nullable List<String> findGroupOrPersistentServerTags(ModelsServerSummary serverSummary) {
        if (serverSummary.getServerGroup() != null) {
            return serverSummary.getServerGroup().getTags();
        }
        if (serverSummary.getPersistentServer() != null) {
            return serverSummary.getPersistentServer().getTags();
        }
        return null;
    }

    // In each filter check below, a filter with nothing asked passes everything.
    private static boolean passesValueFilter(@Nullable Object value, @Nullable List<?> asked) {
        if (asked == null || asked.isEmpty()) {
            return true;
        }
        return asked.contains(value);
    }

    // Id filters ignore case, as the controller's list route parses asked ids as UUIDs. Lookups by id compare text, as their HTTP path does.
    private static boolean passesIdListFilter(@Nullable String id, @Nullable List<String> askedIds) {
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

    private static boolean passesIdFilter(@Nullable String id, @Nullable String askedId) {
        if (askedId == null || askedId.isBlank()) {
            return true;
        }
        return askedId.equalsIgnoreCase(id);
    }

    private static boolean passesTagFilter(@Nullable List<String> tags, @Nullable List<String> askedTags) {
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
