package app.simplecloud.api.cache;

import java.time.Duration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Per-entity-type cache configuration overrides.
 *
 * @param staleTime the stale time for this entity type, or null to use the default
 * @param cacheTime the cache time for this entity type, or null to use the default
 * @param enabled whether caching is enabled for this entity type
 */
public record EntityCacheConfig(
        @Nullable Duration staleTime, @Nullable Duration cacheTime, boolean enabled) {

    /**
     * @deprecated use {@link #staleTime()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public Duration getStaleTime() {
        return staleTime;
    }

    /**
     * @deprecated use {@link #cacheTime()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public Duration getCacheTime() {
        return cacheTime;
    }

    /**
     * @deprecated use {@link #enabled()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
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
            return new EntityCacheConfig(staleTime, cacheTime, enabled);
        }
    }
}
