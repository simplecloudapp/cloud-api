package app.simplecloud.api.blueprint;

import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public record RuntimeConfig(
        @Nullable RuntimeType type, @Nullable Map<String, Object> with) {

    /**
     * @deprecated use {@link #type()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public RuntimeType getType() {
        return type;
    }

    /**
     * @deprecated use {@link #with()}; will be removed in 1.0.0
     */
    @Deprecated(forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.0.0")
    @Nullable public Map<String, Object> getWith() {
        return with;
    }

    public static Builder builder() {
        return new Builder();
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
            return new RuntimeConfig(type, with);
        }
    }
}
