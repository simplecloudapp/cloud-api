package app.simplecloud.api;

public class CloudApiOptions {

    public final static CloudApiOptions DEFAULT = new Builder().build();

    private final String natsUrl;
    private final String controllerUrl;
    private final String networkId;
    private final String networkSecret;

    private CloudApiOptions(Builder builder) {
        this.natsUrl = builder.natsUrl;
        this.controllerUrl = builder.controllerUrl;
        this.networkId = builder.networkId;
        this.networkSecret = builder.networkSecret;
    }

    public String getNatsUrl() {
        return natsUrl;
    }

    public String getControllerUrl() {
        return controllerUrl;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getNetworkSecret() {
        return networkSecret;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String natsUrl;
        private String controllerUrl;
        private String networkId;
        private String networkSecret;

        public Builder() {
            this.natsUrl = System.getenv().getOrDefault("SIMPLECLOUD_NATS_URL", "nats://localhost:4222");
            this.controllerUrl = System.getenv().getOrDefault("SIMPLECLOUD_CONTROLLER_URL", "http://localhost:1337");
            this.networkId = System.getenv().getOrDefault("SIMPLECLOUD_NETWORK_ID", "default");
            this.networkSecret = System.getenv().getOrDefault("SIMPLECLOUD_NETWORK_SECRET", "");
        }

        public Builder natsUrl(String natsUrl) {
            this.natsUrl = natsUrl;
            return this;
        }

        public Builder controllerUrl(String controllerUrl) {
            this.controllerUrl = controllerUrl;
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

        public CloudApiOptions build() {
            return new CloudApiOptions(this);
        }
    }
}
