package app.simplecloud.api.internal.create;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateRequestDefaultsTest {

    private final CreateRequestDefaults defaults = new CreateRequestDefaults();

    @Test
    void missingScalingGetsDefaults() {
        ScalingConfig scaling = defaults.defaultGroupScaling(null);

        assertEquals(0, scaling.getMinServers());
        assertEquals(1, scaling.getMaxServers());
        assertEquals(0.75, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.SLOTS, scaling.getScalingMode());
        assertEquals("3m", scaling.getScaleDown().getIdleTime());
        assertTrue(scaling.getScaleDown().isIgnorePlayers());
    }

    @Test
    void explicitScalingValuesAreKept() {
        ScalingConfig scaling = defaults.defaultGroupScaling(ScalingConfig.builder()
                .minServers(2)
                .maxServers(5)
                .playerThreshold(0.5)
                .scalingMode(ScalingMode.SERVERS)
                .scaleDown(ScaleDownConfig.builder().ignorePlayers(false).build())
                .build());

        assertEquals(2, scaling.getMinServers());
        assertEquals(5, scaling.getMaxServers());
        assertEquals(0.5, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.SERVERS, scaling.getScalingMode());
        assertFalse(scaling.getScaleDown().isIgnorePlayers());
        assertEquals("3m", scaling.getScaleDown().getIdleTime());
    }

    @Test
    void playerThresholdOutsideZeroToOneIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> defaults.defaultGroupScaling(
                        ScalingConfig.builder().playerThreshold(0).build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> defaults.defaultGroupScaling(
                        ScalingConfig.builder().playerThreshold(1.5).build()));
    }

    @Test
    void tagsDefaultToTheName() {
        assertEquals(List.of("lobby"), defaults.defaultTags("lobby", null));
        assertEquals(List.of("lobby"), defaults.defaultTags("lobby", List.of()));
        assertEquals(List.of("minigame"), defaults.defaultTags("lobby", List.of("minigame")));
    }

    @Test
    void missingWorkflowPartsGetDefaults() {
        WorkflowsConfig workflows = defaults.defaultWorkflows(
                WorkflowsConfig.builder().manual(List.of("custom/backup")).build());

        assertEquals(List.of("custom/backup"), workflows.getManual());
        assertEquals(List.of("internal/setup"), workflows.getWhen().getStart());
        assertEquals(List.of("internal/cleanup"), workflows.getWhen().getStop());
    }

    @Test
    void inlineBlueprintGetsJavaDefaultsAndKeepsExplicitOptions() {
        CreateBlueprintRequest blueprint = defaults.defaultInlineBlueprint(
                CreateBlueprintRequest.builder()
                        .serverSoftware("paper")
                        .runtimeConfig(RuntimeConfig.builder()
                                .with(Map.of("version", "17"))
                                .build())
                        .build(),
                defaults.defaultWorkflows(null));

        RuntimeConfig runtime = blueprint.getRuntimeConfig();
        assertEquals(RuntimeType.JAVA, runtime.getType());
        assertEquals("17", runtime.getWith().get("version"));
        assertEquals(List.of("nogui"), runtime.getWith().get("args"));
        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());
    }

    @Test
    void noInlineBlueprintStaysNull() {
        assertNull(defaults.defaultInlineBlueprint(null, defaults.defaultWorkflows(null)));
    }
}
