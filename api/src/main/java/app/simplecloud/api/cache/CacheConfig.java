package app.simplecloud.api.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the query cache, inspired by TanStack Query.
 *
 * <p>Key concepts:
 * <ul>
 *   <li><b>staleTime</b>: How long data is considered fresh. Fresh data is returned immediately
 *       without network request. Default: 5 seconds (varies by entity type).</li>
 *   <li><b>cacheTime</b>: How long data stays in memory after it was last fetched or set.
 *       Reads do not extend it, so it is not an idle timeout. Default: 2 minutes.</li>
 *   <li><b>autoInvalidateOnEvents</b>: When enabled, NATS events automatically invalidate
 *       affected cache entries. Default: true.</li>
 * </ul>
 *
 * <p>Default stale times by entity type (optimized for typical usage patterns):
 * <ul>
 *   <li>SERVER: 3 seconds (metrics like CPU/memory may change without events)</li>
 *   <li>GROUP: 10 seconds (configuration rarely changes, events handle updates)</li>
 *   <li>PERSISTENT_SERVER: 10 seconds (similar to groups)</li>
 *   <li>PLAYER: 5 seconds (balance between freshness and load)</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Use defaults - recommended for most cases
 * CloudApi api = CloudApi.create();
 *
 * // Custom configuration
 * CacheConfig config = CacheConfig.builder()
 *     .defaultStaleTime(Duration.ofSeconds(10))
 *     .defaultCacheTime(Duration.ofMinutes(5))
 *     .entityConfig(EntityType.SERVER, EntityCacheConfig.builder()
 *         .staleTime(Duration.ofSeconds(1))  // Need very fresh server data
 *         .build())
 *     .build();
 * }</pre>
 */
public class CacheConfig {

    /**
     * A disabled cache configuration. All operations pass through to the network.
     */
    public static final CacheConfig DISABLED = builder().enabled(false).build();

    /**
     * The default cache configuration with sensible defaults.
     */
    public static final CacheConfig DEFAULT = builder().build();

    private final boolean enabled;
    private final Duration defaultStaleTime;
    private final Duration defaultCacheTime;
    private final Map<EntityType, EntityCacheConfig> entityConfigs;
    private final boolean autoInvalidateOnEvents;
    private final int maxEntries;
    private final Duration eventBatchWindow;

    private CacheConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.defaultStaleTime = builder.defaultStaleTime;
        this.defaultCacheTime = builder.defaultCacheTime;
        this.entityConfigs = Collections.unmodifiableMap(new HashMap<>(builder.entityConfigs));
        this.autoInvalidateOnEvents = builder.autoInvalidateOnEvents;
        this.maxEntries = builder.maxEntries;
        this.eventBatchWindow = builder.eventBatchWindow;
    }

    /**
     * Returns whether caching is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the default stale time for all entity types.
     */
    public Duration getDefaultStaleTime() {
        return defaultStaleTime;
    }

    /**
     * Returns the default cache time for all entity types.
     */
    public Duration getDefaultCacheTime() {
        return defaultCacheTime;
    }

    /**
     * Returns the entity-specific cache configurations.
     */
    public Map<EntityType, EntityCacheConfig> getEntityConfigs() {
        return entityConfigs;
    }

    /**
     * Returns whether automatic cache invalidation on NATS events is enabled.
     */
    public boolean isAutoInvalidateOnEvents() {
        return autoInvalidateOnEvents;
    }

    /**
     * Returns the maximum number of entries in the cache.
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * Returns the batching window for event-based cache invalidation.
     * The first event opens the window, and events arriving before it closes are applied together
     * when it does. Later events do not extend the window.
     */
    public Duration getEventBatchWindow() {
        return eventBatchWindow;
    }

    /**
     * Returns the batching window for event-based cache invalidation.
     *
     * @deprecated Use {@link #getEventBatchWindow()} instead. The window is fixed; later events
     *             do not extend it as a debounce would.
     */
    @Deprecated
    public Duration getEventDebounceTime() {
        return getEventBatchWindow();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = true;
        private Duration defaultStaleTime = Duration.ofSeconds(5);
        private Duration defaultCacheTime = Duration.ofMinutes(2);
        private Map<EntityType, EntityCacheConfig> entityConfigs = createDefaultEntityConfigs();
        private boolean autoInvalidateOnEvents = true;
        private int maxEntries = 500;
        private Duration eventBatchWindow = Duration.ofMillis(50);

        private static Map<EntityType, EntityCacheConfig> createDefaultEntityConfigs() {
            Map<EntityType, EntityCacheConfig> configs = new HashMap<>();
            // Server metrics (CPU, memory, player count) change without events.
            configs.put(EntityType.SERVER, staleAfter(Duration.ofSeconds(3)));
            // Groups and persistent servers rarely change, and events invalidate their entries
            // when they do.
            configs.put(EntityType.GROUP, staleAfter(Duration.ofSeconds(10)));
            configs.put(EntityType.PERSISTENT_SERVER, staleAfter(Duration.ofSeconds(10)));
            // Players: a balance between freshness and load on the controller.
            configs.put(EntityType.PLAYER, staleAfter(Duration.ofSeconds(5)));
            return configs;
        }

        private static EntityCacheConfig staleAfter(Duration staleTime) {
            return EntityCacheConfig.builder()
                    .staleTime(staleTime)
                    .build();
        }

        /**
         * Sets whether caching is enabled globally.
         *
         * @param enabled true to enable caching, false to disable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the default stale time. Data older than this is considered stale
         * and will trigger a background refresh while still returning cached data.
         *
         * @param staleTime the stale time (default: 5 seconds)
         * @return this builder
         */
        public Builder defaultStaleTime(Duration staleTime) {
            this.defaultStaleTime = staleTime;
            return this;
        }

        /**
         * Sets the default cache time. Data older than this will be evicted from cache.
         *
         * @param cacheTime the cache time (default: 2 minutes)
         * @return this builder
         */
        public Builder defaultCacheTime(Duration cacheTime) {
            this.defaultCacheTime = cacheTime;
            return this;
        }

        /**
         * Adds entity-specific cache configuration.
         *
         * @param entityType the entity type
         * @param config the configuration for this entity type
         * @return this builder
         */
        public Builder entityConfig(EntityType entityType, EntityCacheConfig config) {
            this.entityConfigs.put(entityType, config);
            return this;
        }

        /**
         * Sets whether to automatically invalidate cache entries when NATS events arrive.
         * For example, when a ServerUpdatedEvent is received, the cached server data
         * is automatically invalidated.
         *
         * @param autoInvalidate true to enable auto-invalidation (default: true)
         * @return this builder
         */
        public Builder autoInvalidateOnEvents(boolean autoInvalidate) {
            this.autoInvalidateOnEvents = autoInvalidate;
            return this;
        }

        /**
         * Sets the maximum number of entries in the cache.
         * When exceeded, least recently used entries are evicted.
         *
         * @param maxEntries the maximum number of entries (default: 500)
         * @return this builder
         */
        public Builder maxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        /**
         * Sets the batching window for event-based cache invalidation.
         * The first event opens the window, and events arriving before it closes are
         * applied together when it does. Later events do not extend the window, so
         * during a steady stream of events, such as a rolling restart, each event is
         * still applied no later than one window after it arrives.
         *
         * @param batchWindow the window length (default: 50ms)
         * @return this builder
         */
        public Builder eventBatchWindow(Duration batchWindow) {
            this.eventBatchWindow = batchWindow;
            return this;
        }

        /**
         * Sets the batching window for event-based cache invalidation.
         *
         * @param debounceTime the window length (default: 50ms)
         * @return this builder
         * @deprecated Use {@link #eventBatchWindow(Duration)} instead. The window is fixed; later
         *             events do not extend it as a debounce would.
         */
        @Deprecated
        public Builder eventDebounceTime(Duration debounceTime) {
            return eventBatchWindow(debounceTime);
        }

        public CacheConfig build() {
            return new CacheConfig(this);
        }
    }
}
