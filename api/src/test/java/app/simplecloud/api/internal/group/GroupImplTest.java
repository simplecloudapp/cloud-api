package app.simplecloud.api.internal.group;

import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.web.models.ModelsScalingConfig;
import app.simplecloud.api.web.models.ModelsScalingMode;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GroupImplTest {

    @Test
    void getScaling_mapsServersModeToPlayers() {
        ModelsScalingConfig scaling = new ModelsScalingConfig();
        scaling.setMinServers(1);
        scaling.setMaxServers(2);
        scaling.setAvailableSlots(25);
        scaling.setPlayerThreshold(java.math.BigDecimal.valueOf(0.75));
        scaling.setScalingMode(ModelsScalingMode.fromValue("SERVERS"));

        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
        summary.setServerGroupId("group-1");
        summary.setName("Lobby");
        summary.setType(GroupServerType.SERVER.name());
        summary.setScaling(scaling);
        summary.setCreatedAt("2026-04-22T00:00:00Z");
        summary.setUpdatedAt("2026-04-22T00:00:00Z");

        Group group = new GroupImpl(summary);

        assertNotNull(group.getScaling());
        assertEquals(ScalingMode.PLAYERS, group.getScaling().getScalingMode());
    }
}
