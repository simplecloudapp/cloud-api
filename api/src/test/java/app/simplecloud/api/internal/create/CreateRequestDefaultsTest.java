package app.simplecloud.api.internal.create;

import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateRequestDefaultsTest {

    private final CreateRequestDefaults defaults = new CreateRequestDefaults();

    @Test
    void missingScalingGetsDefaults() {
        ScalingConfig scaling = defaults.defaultGroupScaling(null, 50);

        assertEquals(50, scaling.getAvailableSlots());
        assertEquals(0, scaling.getMinServers());
        assertEquals(1, scaling.getMaxServers());
        assertEquals(0.75, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.SLOTS, scaling.getScalingMode());
        ScaleDownConfig scaleDown = scaling.getScaleDown();
        assertNotNull(scaleDown);
        assertEquals("3m", scaleDown.getIdleTime());
        assertTrue(scaleDown.isIgnorePlayers());
    }

    @Test
    void explicitScalingValuesAreKept() {
        ScalingConfig scaling = defaults.defaultGroupScaling(
                ScalingConfig.builder()
                        .minServers(2)
                        .maxServers(5)
                        .playerThreshold(0.5)
                        .scalingMode(ScalingMode.SERVERS)
                        .scaleDown(
                                ScaleDownConfig.builder().ignorePlayers(false).build())
                        .build(),
                50);

        assertEquals(0, scaling.getAvailableSlots());
        assertEquals(2, scaling.getMinServers());
        assertEquals(5, scaling.getMaxServers());
        assertEquals(0.5, scaling.getPlayerThreshold());
        assertEquals(ScalingMode.SERVERS, scaling.getScalingMode());
        ScaleDownConfig scaleDown = scaling.getScaleDown();
        assertNotNull(scaleDown);
        assertFalse(scaleDown.isIgnorePlayers());
        assertEquals("3m", scaleDown.getIdleTime());
    }

    @Test
    void playerThresholdOutsideZeroToOneIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> defaults.defaultGroupScaling(
                        ScalingConfig.builder().playerThreshold(0).build(), 50));
        assertThrows(
                IllegalArgumentException.class,
                () -> defaults.defaultGroupScaling(
                        ScalingConfig.builder().playerThreshold(1.5).build(), 50));
    }

    @Test
    void slotsScalingWithoutSlotsOrMinServersGetsMaxPlayersAsSlots() {
        assertEquals(
                20,
                defaults.defaultGroupScaling(
                                ScalingConfig.builder().maxServers(3).build(), 20)
                        .getAvailableSlots());
        assertEquals(
                5,
                defaults.defaultGroupScaling(
                                ScalingConfig.builder().availableSlots(5).build(), 20)
                        .getAvailableSlots());
        assertEquals(
                0,
                defaults.defaultGroupScaling(
                                ScalingConfig.builder().minServers(1).build(), 20)
                        .getAvailableSlots());
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
        WorkflowWhen when = workflows.getWhen();
        assertNotNull(when);
        assertEquals(List.of("internal/setup"), when.getStart());
        assertEquals(List.of("internal/cleanup"), when.getStop());
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
        assertNotNull(runtime);
        assertEquals(RuntimeType.JAVA, runtime.getType());
        Map<String, Object> with = runtime.getWith();
        assertNotNull(with);
        assertEquals("17", with.get("version"));
        assertEquals(List.of("nogui"), with.get("args"));
        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());
    }

    @Test
    void noInlineBlueprintStaysNull() {
        assertNull(defaults.defaultInlineBlueprint(null, defaults.defaultWorkflows(null)));
    }
}
