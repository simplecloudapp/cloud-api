package app.simplecloud.api.group;

import org.jetbrains.annotations.Nullable;

public class SourceConfig {
    @Nullable
    private SourceType type;
    @Nullable
    private String blueprint;
    @Nullable
    private String image;

    public SourceConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public SourceType getType() {
        return type;
    }

    public void setType(@Nullable SourceType type) {
        this.type = type;
    }

    @Nullable
    public String getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(@Nullable String blueprint) {
        this.blueprint = blueprint;
    }

    @Nullable
    public String getImage() {
        return image;
    }

    public void setImage(@Nullable String image) {
        this.image = image;
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
            SourceConfig config = new SourceConfig();
            config.setType(type);
            config.setBlueprint(blueprint);
            config.setImage(image);
            return config;
        }
    }
}
