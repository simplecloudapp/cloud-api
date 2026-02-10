package app.simplecloud.api;

import app.simplecloud.api.cache.CacheConfig;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloudApiOptions {

    public final static CloudApiOptions DEFAULT = new Builder().build();
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    private final String natsUrl;
    private final Duration natsFailoverReconnectAfter;
    private final String controllerUrl;
    private final String component;
    private final String networkId;
    private final String networkSecret;
    private final CacheConfig cacheConfig;

    private CloudApiOptions(Builder builder) {
        this.natsUrl = builder.natsUrl;
        this.natsFailoverReconnectAfter = builder.natsFailoverReconnectAfter;
        this.controllerUrl = builder.controllerUrl;
        this.component = builder.component;
        this.networkId = builder.networkId;
        this.networkSecret = builder.networkSecret;
        this.cacheConfig = builder.cacheConfig;
    }

    public String getNatsUrl() {
        return natsUrl;
    }

    public Duration getNatsFailoverReconnectAfter() {
        return natsFailoverReconnectAfter;
    }

    public String getControllerUrl() {
        return controllerUrl;
    }

    public String getComponent() {
        return component;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getNetworkSecret() {
        return networkSecret;
    }

    /**
     * Returns the cache configuration.
     *
     * @return the cache configuration
     */
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String natsUrl;
        private Duration natsFailoverReconnectAfter;
        private String controllerUrl;
        private String component;
        private String networkId;
        private String networkSecret;
        private CacheConfig cacheConfig = CacheConfig.DEFAULT;

        public Builder() {
            this.natsUrl = System.getenv().getOrDefault("SIMPLECLOUD_NATS_URL", "nats://platform.simplecloud.app:4222");
            this.natsFailoverReconnectAfter = parseDuration(
                    System.getenv("SIMPLECLOUD_NATS_FAILOVER_RECONNECT_AFTER"),
                    Duration.ofSeconds(30)
            );
            this.controllerUrl = System.getenv().getOrDefault("SIMPLECLOUD_CONTROLLER_URL", "https://controller.platform.simplecloud.app");
            this.networkId = System.getenv().getOrDefault("SIMPLECLOUD_NETWORK_ID", "default");
            this.networkSecret = System.getenv().getOrDefault("SIMPLECLOUD_NETWORK_SECRET", "");
        }

        public Builder natsUrl(String natsUrl) {
            this.natsUrl = natsUrl;
            return this;
        }

        public Builder natsFailoverReconnectAfter(Duration natsFailoverReconnectAfter) {
            if (natsFailoverReconnectAfter == null) {
                throw new IllegalArgumentException("natsFailoverReconnectAfter must not be null");
            }
            if (natsFailoverReconnectAfter.isNegative()) {
                throw new IllegalArgumentException("natsFailoverReconnectAfter must be >= 0");
            }
            this.natsFailoverReconnectAfter = natsFailoverReconnectAfter;
            return this;
        }

        public Builder controllerUrl(String controllerUrl) {
            this.controllerUrl = controllerUrl;
            return this;
        }

        /**
         * Sets the value for the controller request header {@code X-SC-Component}.
         *
         * @param component the component identifier to send; null/blank disables the header
         * @return this builder
         */
        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder networkId(String networkId) {
            this.networkId = networkId;
            return this;
        }

        public Builder networkSecret(String networkSecret) {
            this.networkSecret = networkSecret;
            return this;
        }

        /**
         * Sets the cache configuration.
         *
         * <p>By default, caching is enabled with sensible defaults.
         * Use {@link CacheConfig#DISABLED} to disable caching entirely.
         *
         * @param cacheConfig the cache configuration
         * @return this builder
         */
        public Builder cache(CacheConfig cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Disables caching entirely.
         * Shorthand for {@code cache(CacheConfig.DISABLED)}.
         *
         * @return this builder
         */
        public Builder disableCache() {
            this.cacheConfig = CacheConfig.DISABLED;
            return this;
        }

        public CloudApiOptions build() {
            return new CloudApiOptions(this);
        }

        private Duration parseDuration(String raw, Duration defaultValue) {
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }

            String value = raw.trim().toLowerCase();
            Matcher matcher = DURATION_PATTERN.matcher(value);
            if (matcher.matches()) {
                long amount = Long.parseLong(matcher.group(1));
                return switch (matcher.group(2)) {
                    case "ms" -> Duration.ofMillis(amount);
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    default -> defaultValue;
                };
            }

            try {
                Duration duration = Duration.parse(raw);
                if (duration.isNegative()) {
                    throw new IllegalArgumentException("Duration must be >= 0");
                }
                return duration;
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid SIMPLECLOUD_NATS_FAILOVER_RECONNECT_AFTER value '" + raw +
                                "'. Use values like 30s, 2m, 1h, 500ms, or ISO-8601 (e.g. PT30S).",
                        e
                );
            }
        }
    }
}
