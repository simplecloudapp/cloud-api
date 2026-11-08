package app.simplecloud.api.internal.event;

import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.server.Server;
import build.buf.gen.simplecloud.controller.v2.BaseServerConfig;
import build.buf.gen.simplecloud.controller.v2.BlueprintConfig;
import build.buf.gen.simplecloud.controller.v2.PersistentServerConfig;
import build.buf.gen.simplecloud.controller.v2.ServerGroupConfig;
import build.buf.gen.simplecloud.controller.v2.ServerRuntimeInfo;
import build.buf.gen.simplecloud.controller.v2.ServerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ControllerEventModelMapperTest {

    private static final String GROUP_ID = "0b6e2c4a-7d1f-4e3b-9a5c-2f8d6e1b3c7a";
    private static final String SERVER_ID = "3c9a1f2e-6b4d-4e8a-9f1c-2d7e5b8a0c34";
    private static final String PERSISTENT_SERVER_ID = "7f3e1d5c-9b2a-4c6e-8d0f-1a3b5c7d9e2f";
    private static final long TIMESTAMP = 1_700_000_000L;

    @Test
    void mapGroupReadsGroupConfig() {
        ServerGroupConfig config = ServerGroupConfig.newBuilder()
                .setBaseConfig(BaseServerConfig.newBuilder()
                        .setName("lobby")
                        .setType(ServerType.SERVER_TYPE_SERVER)
                        .addTags("lobby"))
                .setScaling(build.buf.gen.simplecloud.controller.v2.ScalingConfig.newBuilder()
                        .setMinServers(1)
                        .setMaxServers(3)
                        .setScalingMode(build.buf.gen.simplecloud.controller.v2.ScalingMode.SCALING_MODE_SLOTS))
                .build();

        Group group = ControllerEventModelMapper.mapGroup(GROUP_ID, config, TIMESTAMP);

        assertEquals(GROUP_ID, group.getServerGroupId());
        assertEquals("lobby", group.getName());
        assertEquals(GroupServerType.SERVER, group.getType());
        assertEquals(List.of("lobby"), group.getTags());
        ScalingConfig scaling = group.getScaling();
        assertNotNull(scaling);
        assertEquals(1, scaling.getMinServers());
        assertEquals(3, scaling.getMaxServers());
        assertEquals(ScalingMode.SLOTS, scaling.getScalingMode());
    }

    @Test
    void mapBlueprintReadsBlueprintConfig() {
        BlueprintConfig config = BlueprintConfig.newBuilder()
                .setBlueprintId("5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d")
                .setName("paper")
                .setServerSoftware("paper")
                .setMinecraftVersion("1.21.4")
                .addWorkflowSteps("internal/setup")
                .setRuntime(build.buf.gen.simplecloud.controller.v2.RuntimeConfig.newBuilder()
                        .setType(build.buf.gen.simplecloud.controller.v2.RuntimeType.RUNTIME_TYPE_JAVA)
                        .putWith("version", "\"21\""))
                .build();

        Blueprint blueprint = ControllerEventModelMapper.mapBlueprint(config, TIMESTAMP);

        assertEquals("5a1c9e2b-3d4f-4a6b-8c7d-9e0f1a2b3c4d", blueprint.getBlueprintId());
        assertEquals("paper", blueprint.getName());
        assertEquals("1.21.4", blueprint.getMinecraftVersion());
        assertNull(blueprint.getServerUrl());
        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());
        RuntimeConfig runtime = blueprint.getRuntimeConfig();
        assertNotNull(runtime);
        assertEquals(RuntimeType.JAVA, runtime.getType());
        assertEquals(Map.of("version", "21"), runtime.getWith());
    }

    @Test
    void mapServerPrefersRuntimeMaxPlayersAndFallsBackToConfig() {
        BaseServerConfig config =
                BaseServerConfig.newBuilder().setName("lobby").setMaxPlayers(50).build();

        Server withRuntime = ControllerEventModelMapper.mapServer(
                "network",
                SERVER_ID,
                GROUP_ID,
                "",
                config,
                ServerRuntimeInfo.newBuilder()
                        .setNumericalId(1)
                        .setPlayerCount(7)
                        .setMaxPlayers(80)
                        .build(),
                null,
                null,
                null,
                TIMESTAMP);
        Server withoutRuntimeLimit = ControllerEventModelMapper.mapServer(
                "network",
                SERVER_ID,
                GROUP_ID,
                "",
                config,
                ServerRuntimeInfo.newBuilder().setNumericalId(1).build(),
                null,
                null,
                null,
                TIMESTAMP);

        assertEquals(7, withRuntime.getPlayerCount());
        assertEquals(80, withRuntime.getMaxPlayers());
        assertEquals(50, withoutRuntimeLimit.getMaxPlayers());
    }

    @Test
    void groupFromEventCarriesResourcesAndPriority() {
        ServerGroupConfig config = ServerGroupConfig.newBuilder()
                .setBaseConfig(BaseServerConfig.newBuilder()
                        .setName("lobby")
                        .setMinMemory(512)
                        .setMaxMemory(1024)
                        .setMaxPlayers(50)
                        .setActive(true)
                        .setPriority(3))
                .build();

        Group group = ControllerEventModelMapper.mapGroup(GROUP_ID, config, TIMESTAMP);
        Server server = ControllerEventModelMapper.mapServer(
                "network", SERVER_ID, GROUP_ID, "", null, null, null, config, null, TIMESTAMP);

        assertEquals(512, group.getMinMemory());
        assertEquals(1024, group.getMaxMemory());
        assertEquals(50, group.getMaxPlayers());
        assertEquals(Boolean.TRUE, group.isActive());
        assertEquals(3, group.getPriority());
        Group serverGroup = server.getGroup();
        assertNotNull(serverGroup);
        assertEquals(50, serverGroup.getMaxPlayers());
    }

    @Test
    void groupFromEventWithoutPriorityHasNone() {
        ServerGroupConfig config = ServerGroupConfig.newBuilder()
                .setBaseConfig(BaseServerConfig.newBuilder().setName("lobby"))
                .build();

        assertNull(
                ControllerEventModelMapper.mapGroup(GROUP_ID, config, TIMESTAMP).getPriority());
    }

    @Test
    void unspecifiedScalingModeIsLeftOut() {
        ServerGroupConfig config = ServerGroupConfig.newBuilder()
                .setBaseConfig(BaseServerConfig.newBuilder().setName("lobby"))
                .setScaling(build.buf.gen.simplecloud.controller.v2.ScalingConfig.newBuilder()
                        .setMaxServers(3))
                .build();

        ScalingConfig scaling =
                ControllerEventModelMapper.mapGroup(GROUP_ID, config, TIMESTAMP).getScaling();

        assertNotNull(scaling);
        assertNull(scaling.getScalingMode());
    }

    @Test
    void mapPersistentServerReadsConfig() {
        PersistentServerConfig config = PersistentServerConfig.newBuilder()
                .setBaseConfig(BaseServerConfig.newBuilder()
                        .setName("hub")
                        .setType(ServerType.SERVER_TYPE_PROXY)
                        .setMaxPlayers(100))
                .setDeploymentHost("host-1")
                .build();

        PersistentServer persistentServer =
                ControllerEventModelMapper.mapPersistentServer(PERSISTENT_SERVER_ID, config, null, TIMESTAMP);

        assertEquals(PERSISTENT_SERVER_ID, persistentServer.getPersistentServerId());
        assertEquals("hub", persistentServer.getName());
        assertEquals(GroupServerType.PROXY, persistentServer.getType());
        assertEquals(100, persistentServer.getMaxPlayers());
        assertEquals("host-1", persistentServer.getServerhostId());
    }
}
