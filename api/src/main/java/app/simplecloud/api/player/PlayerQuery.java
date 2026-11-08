package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Filters, sorting and paging for {@link PlayerApi#list(PlayerQuery)}.
 *
 * <p>Unset values use the controller defaults: no filter, sorted by {@link SortField#LAST_SEEN}
 * {@link SortOrder#DESC}, at most 50 players. A player has to match all filters that are set.
 *
 * <pre>{@code
 * PlayerQuery query = PlayerQuery.create()
 *         .filterByOnline(true)
 *         .search("steve")
 *         .limit(20);
 * }</pre>
 */
public class PlayerQuery {

    /**
     * The highest value accepted by {@link #limit(int)}.
     */
    public static final int MAX_LIMIT = 1000;

    private Boolean online;
    private String serverName;
    private String proxyName;
    private List<UUID> playerIds;
    private Instant firstSeenFrom;
    private Instant firstSeenTo;
    private Instant lastSeenFrom;
    private Instant lastSeenTo;
    private Long onlineTimeSecondsMin;
    private Long onlineTimeSecondsMax;
    private String search;
    private SortField sortBy;
    private SortOrder sortOrder;
    private Integer limit;
    private Integer offset;

    public PlayerQuery() {}

    public static PlayerQuery create() {
        return new PlayerQuery();
    }

    /**
     * Creates an independent copy of this query.
     *
     * @return a new query with the same filters, sorting and paging
     */
    public PlayerQuery copy() {
        PlayerQuery copy = new PlayerQuery();
        copy.online = online;
        copy.serverName = serverName;
        copy.proxyName = proxyName;
        copy.playerIds = playerIds == null ? null : new ArrayList<>(playerIds);
        copy.firstSeenFrom = firstSeenFrom;
        copy.firstSeenTo = firstSeenTo;
        copy.lastSeenFrom = lastSeenFrom;
        copy.lastSeenTo = lastSeenTo;
        copy.onlineTimeSecondsMin = onlineTimeSecondsMin;
        copy.onlineTimeSecondsMax = onlineTimeSecondsMax;
        copy.search = search;
        copy.sortBy = sortBy;
        copy.sortOrder = sortOrder;
        copy.limit = limit;
        copy.offset = offset;
        return copy;
    }

    public PlayerQuery filterByOnline(boolean online) {
        this.online = online;
        return this;
    }

    /**
     * Only includes players on the server with exactly this name. Implies {@code online = true}.
     *
     * @param serverName the server name
     * @return this query
     */
    public PlayerQuery filterByServer(String serverName) {
        this.serverName = serverName;
        return this;
    }

    /**
     * Only includes players on the proxy with exactly this name. Implies {@code online = true}.
     *
     * @param proxyName the proxy name
     * @return this query
     */
    public PlayerQuery filterByProxy(String proxyName) {
        this.proxyName = proxyName;
        return this;
    }

    /**
     * Only includes the given players. Repeated calls add to the IDs from earlier calls.
     *
     * @param playerIds the player UUIDs
     * @return this query
     */
    public PlayerQuery filterByPlayerId(UUID... playerIds) {
        if (this.playerIds == null) {
            this.playerIds = new ArrayList<>();
        }
        this.playerIds.addAll(Arrays.asList(playerIds));
        return this;
    }

    /**
     * Only includes players first seen in this range. Both bounds are inclusive; null leaves that side open.
     *
     * @param from the earliest time, or null
     * @param to the latest time, or null
     * @return this query
     */
    public PlayerQuery filterByFirstSeen(@Nullable Instant from, @Nullable Instant to) {
        this.firstSeenFrom = from;
        this.firstSeenTo = to;
        return this;
    }

    /**
     * Only includes players last seen in this range. Both bounds are inclusive; null leaves that side open.
     *
     * @param from the earliest time, or null
     * @param to the latest time, or null
     * @return this query
     */
    public PlayerQuery filterByLastSeen(@Nullable Instant from, @Nullable Instant to) {
        this.lastSeenFrom = from;
        this.lastSeenTo = to;
        return this;
    }

    /**
     * Only includes players whose accumulated online time is in this range. Null leaves that side open.
     *
     * @param min the minimum in seconds, or null
     * @param max the maximum in seconds, or null
     * @throws IllegalArgumentException if a bound is negative
     */
    public void filterByOnlineTimeSeconds(@Nullable Long min, @Nullable Long max) {
        if ((min != null && min < 0) || (max != null && max < 0)) {
            throw new IllegalArgumentException("online time bounds must be >= 0");
        }
        this.onlineTimeSecondsMin = min;
        this.onlineTimeSecondsMax = max;
    }

    /**
     * Only includes players whose current name contains this text, ignoring case.
     *
     * @param search the text to search for
     * @return this query
     */
    public PlayerQuery search(String search) {
        this.search = search;
        return this;
    }

    public PlayerQuery sortBy(SortField sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public PlayerQuery sortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }

    /**
     * Sets the maximum number of players returned. Defaults to 50.
     *
     * @param limit the limit, between 1 and {@link #MAX_LIMIT}
     * @return this query
     * @throws IllegalArgumentException if the limit is out of range
     */
    public PlayerQuery limit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.limit = limit;
        return this;
    }

    public PlayerQuery offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.offset = offset;
        return this;
    }

    @Nullable
    public Boolean getOnline() {
        return online;
    }

    @Nullable
    public String getServerName() {
        return serverName;
    }

    @Nullable
    public String getProxyName() {
        return proxyName;
    }

    @Nullable
    public List<UUID> getPlayerIds() {
        return playerIds;
    }

    @Nullable
    public Instant getFirstSeenFrom() {
        return firstSeenFrom;
    }

    @Nullable
    public Instant getFirstSeenTo() {
        return firstSeenTo;
    }

    @Nullable
    public Instant getLastSeenFrom() {
        return lastSeenFrom;
    }

    @Nullable
    public Instant getLastSeenTo() {
        return lastSeenTo;
    }

    @Nullable
    public Long getOnlineTimeSecondsMin() {
        return onlineTimeSecondsMin;
    }

    @Nullable
    public Long getOnlineTimeSecondsMax() {
        return onlineTimeSecondsMax;
    }

    @Nullable
    public String getSearch() {
        return search;
    }

    @Nullable
    public SortField getSortBy() {
        return sortBy;
    }

    @Nullable
    public SortOrder getSortOrder() {
        return sortOrder;
    }

    @Nullable
    public Integer getLimit() {
        return limit;
    }

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    public enum SortField {
        NAME("name"),
        FIRST_SEEN("first_seen"),
        LAST_SEEN("last_seen"),
        ONLINE_TIME("online_time_seconds");

        private final String value;

        SortField(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public enum SortOrder {
        ASC("asc"),
        DESC("desc");

        private final String value;

        SortOrder(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
