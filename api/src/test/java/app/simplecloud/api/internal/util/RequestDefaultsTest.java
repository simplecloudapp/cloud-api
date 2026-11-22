package app.simplecloud.api.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowsConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestDefaultsTest {

    private final RequestDefaults defaults = new RequestDefaults();

    @Test
    void groupScalingDefaultsWhenNothingIsSet() {
        ScalingConfig scaling = defaults.defaultGroupScaling(null);

        assertEquals(new ScalingConfig(null, 1, 0, 0.75, new ScaleDownConfig("3m", true), ScalingMode.SLOTS), scaling);
    }

    @Test
    void groupScalingKeepsExplicitValues() {
        ScalingConfig scaling = defaults.defaultGroupScaling(ScalingConfig.builder()
                .minServers(2)
                .maxServers(8)
                .playerThreshold(0.9)
                .scalingMode(ScalingMode.PLAYERS)
                .scaleDown(ScaleDownConfig.builder().ignorePlayers(false).build())
                .build());

        assertEquals(new ScalingConfig(0, 8, 2, 0.9, new ScaleDownConfig("3m", false), ScalingMode.PLAYERS), scaling);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.5, 1.01})
    void rejectsPlayerThresholdOutsideOfRange(double playerThreshold) {
        ScalingConfig scaling =
                ScalingConfig.builder().playerThreshold(playerThreshold).build();

        assertThrows(IllegalArgumentException.class, () -> defaults.defaultGroupScaling(scaling));
    }

    @Test
    void tagsFallBackToEntityName() {
        assertEquals(List.of("Lobby"), defaults.defaultTags("Lobby", null));
        assertEquals(List.of("Lobby"), defaults.defaultTags("Lobby", List.of()));
        assertEquals(List.of("custom"), defaults.defaultTags("Lobby", List.of("custom")));
    }

    @Test
    void workflowsKeepExplicitManualWorkflowsAndDefaultTheRest() {
        WorkflowsConfig workflows = defaults.defaultWorkflows(
                WorkflowsConfig.builder().manual(List.of("custom/restart")).build());

        assertEquals(List.of("custom/restart"), workflows.manual());
        assertEquals(List.of("internal/setup"), workflows.when().start());
        assertEquals(List.of("internal/cleanup"), workflows.when().stop());
    }

    @Test
    void inlineBlueprintUsesStartWorkflowsAsStepsAndJavaRuntimeDefaults() {
        WorkflowsConfig workflows = defaults.defaultWorkflows(null);

        CreateBlueprintRequest blueprint = defaults.defaultInlineBlueprint(
                CreateBlueprintRequest.builder().configurator("paper").build(), workflows);

        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());
        assertEquals(RuntimeType.JAVA, blueprint.getRuntimeConfig().type());
        assertEquals("21", blueprint.getRuntimeConfig().with().get("version"));
        assertEquals(List.of("nogui"), blueprint.getRuntimeConfig().with().get("args"));
    }

    @Test
    void explicitRuntimeValuesOverrideJavaDefaults() {
        CreateBlueprintRequest blueprint = defaults.defaultBlueprint(CreateBlueprintRequest.builder()
                .runtimeConfig(RuntimeConfig.builder()
                        .type(RuntimeType.JAVA)
                        .with(Map.of("version", "25"))
                        .build())
                .build());

        Map<String, Object> with = blueprint.getRuntimeConfig().with();
        assertEquals("25", with.get("version"));
        assertEquals(List.of("-Dcom.mojang.eula.agree=true"), with.get("options"));
    }

    @Test
    void nonJavaRuntimeGetsNoJavaDefaults() {
        CreateBlueprintRequest blueprint = defaults.defaultBlueprint(CreateBlueprintRequest.builder()
                .runtimeConfig(RuntimeConfig.builder().type(RuntimeType.BINARY).build())
                .build());

        assertEquals(RuntimeType.BINARY, blueprint.getRuntimeConfig().type());
        assertNull(blueprint.getRuntimeConfig().with());
    }
}
