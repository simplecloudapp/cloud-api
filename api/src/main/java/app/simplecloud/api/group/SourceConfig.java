package app.simplecloud.api.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record SourceConfig(
        @Nullable SourceType type,
        @Nullable String blueprint,
        @Nullable String image) {

    /**
     * @deprecated use {@link #type()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public SourceType getType() {
        return type;
    }

    /**
     * @deprecated use {@link #blueprint()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getBlueprint() {
        return blueprint;
    }

    /**
     * @deprecated use {@link #image()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public String getImage() {
        return image;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SourceType type;
        private String blueprint;
        private String image;

        public Builder type(SourceType type) {
            this.type = type;
            return this;
        }

        public Builder blueprint(String blueprint) {
            this.blueprint = blueprint;
            return this;
        }

        public Builder image(String image) {
            this.image = image;
            return this;
        }

        public SourceConfig build() {
            return new SourceConfig(type, blueprint, image);
        }
    }
}
