package app.simplecloud.api.cache;

import java.time.Duration;

/**
 * Per-entity-type cache configuration overrides.
 * Use this to set different freshness requirements for different entity types.
 *
 * <p>Example:
 * <pre>{@code
 * EntityCacheConfig serverConfig = EntityCacheConfig.builder()
 *     .staleTime(Duration.ofSeconds(2))  // Servers need fresher data
 *     .cacheTime(Duration.ofMinutes(2))
 *     .build();
 * }</pre>
 */
public class EntityCacheConfig {

    private final Duration staleTime;
    private final Duration cacheTime;
    private final boolean enabled;

    private EntityCacheConfig(Builder builder) {
        this.staleTime = builder.staleTime;
        this.cacheTime = builder.cacheTime;
        this.enabled = builder.enabled;
    }

    /**
     * Returns the stale time for this entity type, or null to use the default.
     */
    public Duration getStaleTime() {
        return staleTime;
    }

    /**
     * Returns the cache time for this entity type, or null to use the default.
     */
    public Duration getCacheTime() {
        return cacheTime;
    }

    /**
     * Returns whether caching is enabled for this entity type.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration staleTime = null;
        private Duration cacheTime = null;
        private boolean enabled = true;

        /**
         * Sets the stale time for this entity type.
         * Data older than this is considered stale and will trigger background refresh.
         *
         * @param staleTime the stale time
         * @return this builder
         */
        public Builder staleTime(Duration staleTime) {
            this.staleTime = staleTime;
            return this;
        }

        /**
         * Sets the cache time for this entity type.
         * Data older than this will be evicted from cache.
         *
         * @param cacheTime the cache time
         * @return this builder
         */
        public Builder cacheTime(Duration cacheTime) {
            this.cacheTime = cacheTime;
            return this;
        }

        /**
         * Sets whether caching is enabled for this entity type.
         *
         * @param enabled true to enable caching, false to disable
         * @return this builder
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public EntityCacheConfig build() {
            return new EntityCacheConfig(this);
        }
    }
}
