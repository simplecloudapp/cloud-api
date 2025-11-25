package app.simplecloud.api.blueprint;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class RuntimeConfig {
    @Nullable
    private RuntimeType type;
    @Nullable
    private Map<String, Object> with;

    public RuntimeConfig() {
    }

    @Nullable
    public RuntimeType getType() {
        return type;
    }

    public void setType(@Nullable RuntimeType type) {
        this.type = type;
    }

    @Nullable
    public Map<String, Object> getWith() {
        return with;
    }

    public void setWith(@Nullable Map<String, Object> with) {
        this.with = with;
    }
}