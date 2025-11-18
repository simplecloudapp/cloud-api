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
}


