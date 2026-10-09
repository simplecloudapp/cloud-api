package app.simplecloud.api.player;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PlayerQuery {

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

    public PlayerQuery() {
    }

    public static PlayerQuery create() {
        return new PlayerQuery();
    }

    public PlayerQuery filterByOnline(boolean online) {
        this.online = online;
        return this;
    }

    public PlayerQuery filterByServer(String serverName) {
        this.serverName = serverName;
        return this;
    }

    public PlayerQuery filterByProxy(String proxyName) {
        this.proxyName = proxyName;
        return this;
    }

    public PlayerQuery filterByPlayerId(UUID... playerIds) {
        if (this.playerIds == null) {
            this.playerIds = new ArrayList<>();
        }
        this.playerIds.addAll(Arrays.asList(playerIds));
        return this;
    }

    public PlayerQuery filterByFirstSeen(@Nullable Instant from, @Nullable Instant to) {
        this.firstSeenFrom = from;
        this.firstSeenTo = to;
        return this;
    }

    public PlayerQuery filterByLastSeen(@Nullable Instant from, @Nullable Instant to) {
        this.lastSeenFrom = from;
        this.lastSeenTo = to;
        return this;
    }

    public PlayerQuery filterByOnlineTimeSeconds(@Nullable Long min, @Nullable Long max) {
        if ((min != null && min < 0) || (max != null && max < 0)) {
            throw new IllegalArgumentException("online time bounds must be >= 0");
        }
        this.onlineTimeSecondsMin = min;
        this.onlineTimeSecondsMax = max;
        return this;
    }

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
