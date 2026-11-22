package app.simplecloud.api.internal.util;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDefaultsTest {

    private final RequestDefaults defaults = new RequestDefaults();

    @Test
    void groupScalingDefaultsWhenNothingIsSet() {
        ScalingConfig scaling = defaults.defaultGroupScaling(null);

        assertEquals(0, scaling.getMinServers());
        assertEquals(1, scaling.getMaxServers());
        assertEquals(0.75, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.SLOTS, scaling.getScalingMode());
        assertEquals("3m", scaling.getScaleDown().getIdleTime());
        assertTrue(scaling.getScaleDown().isIgnorePlayers());
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

        assertEquals(2, scaling.getMinServers());
        assertEquals(8, scaling.getMaxServers());
        assertEquals(0.9, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.PLAYERS, scaling.getScalingMode());
        assertEquals("3m", scaling.getScaleDown().getIdleTime());
        assertFalse(scaling.getScaleDown().isIgnorePlayers());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.5, 1.01})
    void rejectsPlayerThresholdOutsideOfRange(double playerThreshold) {
        ScalingConfig scaling = ScalingConfig.builder().playerThreshold(playerThreshold).build();

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
        WorkflowsConfig workflows = defaults.defaultWorkflows(WorkflowsConfig.builder().manual(List.of("custom/restart")).build());

        assertEquals(List.of("custom/restart"), workflows.getManual());
        assertEquals(List.of("internal/setup"), workflows.getWhen().getStart());
        assertEquals(List.of("internal/cleanup"), workflows.getWhen().getStop());
    }

    @Test
    void inlineBlueprintUsesStartWorkflowsAsStepsAndJavaRuntimeDefaults() {
        WorkflowsConfig workflows = defaults.defaultWorkflows(null);

        CreateBlueprintRequest blueprint = defaults.defaultInlineBlueprint(
                CreateBlueprintRequest.builder().configurator("paper").build(),
                workflows
        );

        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());
        assertEquals(RuntimeType.JAVA, blueprint.getRuntimeConfig().getType());
        assertEquals("21", blueprint.getRuntimeConfig().getWith().get("version"));
        assertEquals(List.of("nogui"), blueprint.getRuntimeConfig().getWith().get("args"));
    }

    @Test
    void explicitRuntimeValuesOverrideJavaDefaults() {
        CreateBlueprintRequest blueprint = defaults.defaultBlueprint(CreateBlueprintRequest.builder()
                .runtimeConfig(RuntimeConfig.builder().type(RuntimeType.JAVA).with(Map.of("version", "25")).build())
                .build());

        Map<String, Object> with = blueprint.getRuntimeConfig().getWith();
        assertEquals("25", with.get("version"));
        assertEquals(List.of("-Dcom.mojang.eula.agree=true"), with.get("options"));
    }

    @Test
    void nonJavaRuntimeGetsNoJavaDefaults() {
        CreateBlueprintRequest blueprint = defaults.defaultBlueprint(CreateBlueprintRequest.builder()
                .runtimeConfig(RuntimeConfig.builder().type(RuntimeType.BINARY).build())
                .build());

        assertEquals(RuntimeType.BINARY, blueprint.getRuntimeConfig().getType());
        assertNull(blueprint.getRuntimeConfig().getWith());
    }
}
