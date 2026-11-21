package app.simplecloud.api.internal.blueprint;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.web.models.ModelsBlueprintInfo;
import app.simplecloud.api.web.models.ModelsBlueprintSummary;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsRuntimeConfig;
import app.simplecloud.api.web.models.ModelsUpdateBlueprintResponse;
import build.buf.gen.simplecloud.controller.v2.BlueprintConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BlueprintImpl(
        String blueprintId,
        String name,
        @Nullable String configurator,
        @Nullable String minecraftVersion,
        @Nullable String serverSoftware,
        @Nullable String serverUrl,
        @Nullable String softwareVersion,
        @Nullable RuntimeConfig runtimeConfig,
        @Nullable List<String> workflowSteps,
        @Nullable String createdAt,
        @Nullable String updatedAt
) implements Blueprint {

    private static final Gson GSON = new Gson();

    public static BlueprintImpl from(ModelsBlueprintSummary summary) {
        return new BlueprintImpl(
                summary.getBlueprintId(),
                summary.getName(),
                summary.getConfigurator(),
                summary.getMinecraftVersion(),
                summary.getServerSoftware(),
                summary.getServerUrl(),
                summary.getSoftwareVersion(),
                runtimeConfig(summary.getRuntimeConfig()),
                copy(summary.getWorkflowSteps()),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }

    public static BlueprintImpl from(ModelsCreateBlueprintResponse response) {
        return new BlueprintImpl(
                response.getBlueprintId(),
                response.getName(),
                response.getConfigurator(),
                response.getMinecraftVersion(),
                response.getServerSoftware(),
                response.getServerUrl(),
                response.getSoftwareVersion(),
                runtimeConfig(response.getRuntimeConfig()),
                copy(response.getWorkflowSteps()),
                response.getCreatedAt(),
                response.getUpdatedAt()
        );
    }

    public static BlueprintImpl from(ModelsUpdateBlueprintResponse response) {
        return new BlueprintImpl(
                response.getBlueprintId(),
                response.getName(),
                response.getConfigurator(),
                response.getMinecraftVersion(),
                response.getServerSoftware(),
                response.getServerUrl(),
                response.getSoftwareVersion(),
                runtimeConfig(response.getRuntimeConfig()),
                copy(response.getWorkflowSteps()),
                response.getCreatedAt(),
                response.getUpdatedAt()
        );
    }

    public static BlueprintImpl from(ModelsBlueprintInfo info) {
        return new BlueprintImpl(
                info.getId(),
                info.getName(),
                info.getConfigurator(),
                info.getMinecraftVersion(),
                info.getServerSoftware(),
                info.getServerUrl(),
                info.getSoftwareVersion(),
                runtimeConfig(info.getRuntimeConfig()),
                copy(info.getWorkflowSteps()),
                info.getCreatedAt(),
                info.getUpdatedAt()
        );
    }

    public static BlueprintImpl from(BlueprintConfig config, @Nullable String createdAt, @Nullable String updatedAt) {
        return new BlueprintImpl(
                config.getBlueprintId(),
                config.getName(),
                emptyToNull(config.getConfigurator()),
                emptyToNull(config.getMinecraftVersion()),
                emptyToNull(config.getServerSoftware()),
                emptyToNull(config.getServerUrl()),
                emptyToNull(config.getSoftwareVersion()),
                config.hasRuntime() ? runtimeConfig(config.getRuntime()) : null,
                List.copyOf(config.getWorkflowStepsList()),
                createdAt,
                updatedAt
        );
    }

    @Override
    public String getBlueprintId() {
        return blueprintId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable String getConfigurator() {
        return configurator;
    }

    @Override
    public @Nullable String getMinecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public @Nullable String getServerSoftware() {
        return serverSoftware;
    }

    @Override
    public @Nullable String getServerUrl() {
        return serverUrl;
    }

    @Override
    public @Nullable String getSoftwareVersion() {
        return softwareVersion;
    }

    @Override
    public @Nullable RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    @Override
    public @Nullable List<String> getWorkflowSteps() {
        return workflowSteps;
    }

    @Override
    public @Nullable String getCreatedAt() {
        return createdAt;
    }

    @Override
    public @Nullable String getUpdatedAt() {
        return updatedAt;
    }

    static @Nullable RuntimeType runtimeType(@Nullable String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "JAVA" -> RuntimeType.JAVA;
            case "BINARY" -> RuntimeType.BINARY;
            default -> null;
        };
    }

    private static @Nullable RuntimeConfig runtimeConfig(@Nullable ModelsRuntimeConfig config) {
        if (config == null) {
            return null;
        }
        return RuntimeConfig.builder()
                .type(runtimeType(config.getType()))
                .with(config.getWith() != null ? new LinkedHashMap<>(config.getWith()) : null)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static @Nullable RuntimeConfig runtimeConfig(@Nullable Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return null;
        }
        Object type = config.get("type");
        Object with = config.get("with");
        return RuntimeConfig.builder()
                .type(type instanceof String value ? runtimeType(value) : null)
                .with(with instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : null)
                .build();
    }

    public static Map<String, Object> decodeRuntimeWith(Map<String, String> with) {
        Map<String, Object> result = new LinkedHashMap<>();
        with.forEach((key, value) -> result.put(key, decodeValue(value)));
        return result;
    }

    private static RuntimeConfig runtimeConfig(build.buf.gen.simplecloud.controller.v2.RuntimeConfig config) {
        Map<String, Object> with = decodeRuntimeWith(config.getWithMap());
        RuntimeType type = switch (config.getType()) {
            case RUNTIME_TYPE_JAVA -> RuntimeType.JAVA;
            case RUNTIME_TYPE_BINARY -> RuntimeType.BINARY;
            default -> null;
        };
        return RuntimeConfig.builder()
                .type(type)
                .with(with)
                .build();
    }

    private static Object decodeValue(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            return value;
        }
        try {
            Object decoded = GSON.fromJson(trimmed, Object.class);
            return decoded != null ? decoded : value;
        } catch (JsonParseException e) {
            return value;
        }
    }

    private static @Nullable List<String> copy(@Nullable List<String> values) {
        return values != null ? List.copyOf(values) : null;
    }

    private static @Nullable String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
