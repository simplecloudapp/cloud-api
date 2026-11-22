package app.simplecloud.api.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.group.DeploymentConfig;
import app.simplecloud.api.group.DeploymentHost;
import app.simplecloud.api.group.DeploymentStrategy;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.WorkflowWhen;
import app.simplecloud.api.group.WorkflowsConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigMergeUtilTest {

    @Test
    void scalingUpdateOnlyOverridesFieldsItSets() {
        ScalingConfig current = ScalingConfig.builder()
                .minServers(1)
                .maxServers(5)
                .playerThreshold(0.5)
                .scalingMode(ScalingMode.PLAYERS)
                .scaleDown(ScaleDownConfig.builder()
                        .idleTime("10m")
                        .ignorePlayers(false)
                        .build())
                .build();
        ScalingConfig update = ScalingConfig.builder()
                .maxServers(10)
                .scaleDown(ScaleDownConfig.builder().idleTime("1m").build())
                .build();

        ScalingConfig merged = ConfigMergeUtil.merge(current, update);

        assertEquals(1, merged.getMinServers());
        assertEquals(10, merged.getMaxServers());
        assertEquals(0.5, merged.getPlayerThreshold());
        assertEquals(ScalingMode.PLAYERS, merged.getScalingMode());
        assertEquals("1m", merged.getScaleDown().getIdleTime());
        assertTrue(merged.getScaleDown().hasIgnorePlayers());
        assertFalse(merged.getScaleDown().isIgnorePlayers());
    }

    @Test
    void scalingWithoutUpdateStaysUnchanged() {
        assertNull(ConfigMergeUtil.merge(ScalingConfig.builder().maxServers(3).build(), (ScalingConfig) null));
    }

    @Test
    void scalingFieldsMissingOnBothSidesStayUnset() {
        ScalingConfig merged = ConfigMergeUtil.merge(
                null, ScalingConfig.builder().maxServers(2).build());

        assertTrue(merged.hasMaxServers());
        assertFalse(merged.hasMinServers());
        assertFalse(merged.hasPlayerThreshold());
    }

    @Test
    void deploymentKeepsCurrentHostsAndStrategyWhenUpdateOmitsThem() {
        DeploymentHost host =
                DeploymentHost.builder().name("host-1").priority(1).build();
        DeploymentConfig current = DeploymentConfig.builder()
                .strategy(DeploymentStrategy.WHITELIST)
                .hosts(host)
                .build();

        DeploymentConfig merged = ConfigMergeUtil.merge(current, new DeploymentConfig());

        assertEquals(DeploymentStrategy.WHITELIST, merged.getStrategy());
        assertSame(host, merged.getHosts()[0]);
    }

    @Test
    void deploymentFallsBackToBlacklistWithoutAnyStrategy() {
        DeploymentConfig merged = ConfigMergeUtil.merge(null, new DeploymentConfig());

        assertEquals(DeploymentStrategy.BLACKLIST, merged.getStrategy());
    }

    @Test
    void workflowsMergeStartAndStopSeparately() {
        WorkflowsConfig current = WorkflowsConfig.builder()
                .manual(List.of("default/backup"))
                .when(WorkflowWhen.builder()
                        .start(List.of("internal/setup"))
                        .stop(List.of("internal/cleanup"))
                        .build())
                .build();
        WorkflowsConfig update = WorkflowsConfig.builder()
                .when(WorkflowWhen.builder().start(List.of("custom/setup")).build())
                .build();

        WorkflowsConfig merged = ConfigMergeUtil.merge(current, update);

        assertEquals(List.of("default/backup"), merged.getManual());
        assertEquals(List.of("custom/setup"), merged.getWhen().getStart());
        assertEquals(List.of("internal/cleanup"), merged.getWhen().getStop());
    }
}
