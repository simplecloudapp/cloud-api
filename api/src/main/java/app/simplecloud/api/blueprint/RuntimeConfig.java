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

    public static Builder builder() {
        return new Builder();
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

    public static class Builder {
        private RuntimeType type;
        private Map<String, Object> with;

        public Builder type(RuntimeType type) {
            this.type = type;
            return this;
        }

        public Builder with(Map<String, Object> with) {
            this.with = with;
            return this;
        }

        public RuntimeConfig build() {
            RuntimeConfig config = new RuntimeConfig();
            config.setType(type);
            config.setWith(with);
            return config;
        }
    }
}
