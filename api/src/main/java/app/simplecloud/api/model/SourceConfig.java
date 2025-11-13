package app.simplecloud.api.model;

import org.jetbrains.annotations.Nullable;

public interface SourceConfig {
    String getType();
    @Nullable String getBlueprint();
    @Nullable String getImage();
}
