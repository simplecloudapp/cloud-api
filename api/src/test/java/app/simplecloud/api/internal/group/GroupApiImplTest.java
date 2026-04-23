package app.simplecloud.api.internal.group;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.Group;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.group.ScaleDownConfig;
import app.simplecloud.api.group.ScalingConfig;
import app.simplecloud.api.group.ScalingMode;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.ServerGroupsApi;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsCreateServerGroupRequest;
import app.simplecloud.api.web.models.ModelsCreateServerGroupResponse;
import app.simplecloud.api.web.models.ModelsListServerGroupsResponse;
import app.simplecloud.api.web.models.ModelsServerGroupSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import app.simplecloud.api.web.models.V0BlueprintsPostRequest;
import app.simplecloud.api.web.models.V0ServerGroupsPostRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupApiImplTest {

    @Test
    void createGroup_inlineBlueprint_createsBlueprintFirstAndUsesBlueprintSource() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        RuntimeConfig runtimeConfig = RuntimeConfig.builder()
                .type(RuntimeType.JAVA)
                .with(Map.of("distribution", "temurin"))
                .build();

        CreateBlueprintRequest createBlueprint = CreateBlueprintRequest.builder()
                .configurator("paper")
                .runtimeConfig(runtimeConfig)
                .workflowSteps(List.of("download", "patch"))
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .type(GroupServerType.SERVER)
                .createBlueprint(createBlueprint)
                .build();

        Group group = api.createGroup(request).join();

        assertEquals(1, blueprintsApi.postCalls);
        assertEquals(1, serverGroupsApi.postCalls);
        assertTrue(blueprintsApi.postOrder < serverGroupsApi.postOrder);
        assertEquals("Lobby", blueprintsApi.lastCreateRequest.getName());
        assertNotNull(blueprintsApi.lastCreateRequest.getRuntimeConfig());
        assertEquals("java", blueprintsApi.lastCreateRequest.getRuntimeConfig().getType());
        assertEquals("bp-1", serverGroupsApi.lastCreateRequest.getSource().getBlueprint());
        assertEquals("blueprint", serverGroupsApi.lastCreateRequest.getSource().getType());
        assertNotNull(group.getSource());
        assertEquals("bp-1", group.getSource().getBlueprint());
        assertEquals("Lobby", group.getName());
    }

    @Test
    void createGroup_scalingModeSlots_mapsToGeneratedScalingEnum() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        ScalingConfig scaling = ScalingConfig.builder()
                .minServers(1)
                .maxServers(3)
                .availableSlots(50)
                .playerThreshold(0.8)
                .scalingMode(ScalingMode.SLOTS)
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .type(GroupServerType.SERVER)
                .scaling(scaling)
                .build();

        Group group = api.createGroup(request).join();

        assertNotNull(serverGroupsApi.lastCreateRequest.getScaling());
        assertNotNull(serverGroupsApi.lastCreateRequest.getScaling().getScalingMode());
        assertEquals("SLOTS", serverGroupsApi.lastCreateRequest.getScaling().getScalingMode().getValue());
        assertNotNull(group.getScaling());
        assertEquals(ScalingMode.SLOTS, group.getScaling().getScalingMode());
    }

    @Test
    void createGroup_appliesDashboardDefaults() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder()
                        .configurator("paper")
                        .serverSoftware("paper")
                        .build())
                .build();

        api.createGroup(request).join();

        assertEquals("Lobby", blueprintsApi.lastCreateRequest.getName());
        assertEquals(List.of("internal/setup"), blueprintsApi.lastCreateRequest.getWorkflowSteps());
        assertEquals("java", blueprintsApi.lastCreateRequest.getRuntimeConfig().getType());
        assertEquals("21", blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().get("version"));
        assertEquals(List.of("-Dcom.mojang.eula.agree=true"), blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().get("options"));
        assertEquals(List.of("nogui"), blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().get("args"));

        assertEquals(GroupServerType.SERVER.name(), serverGroupsApi.lastCreateRequest.getType());
        assertEquals(Integer.valueOf(50), serverGroupsApi.lastCreateRequest.getMaxPlayers());
        assertEquals(Boolean.TRUE, serverGroupsApi.lastCreateRequest.getActive());
        assertEquals(List.of("Lobby"), serverGroupsApi.lastCreateRequest.getTags());
        assertEquals(Map.of(), serverGroupsApi.lastCreateRequest.getProperties());
        assertEquals("blacklist", serverGroupsApi.lastCreateRequest.getDeployment().getStrategy());
        assertEquals(BigDecimal.valueOf(0.75), serverGroupsApi.lastCreateRequest.getScaling().getPlayerThreshold());
        assertEquals("SLOTS", serverGroupsApi.lastCreateRequest.getScaling().getScalingMode().getValue());
        assertEquals("3m", serverGroupsApi.lastCreateRequest.getScaling().getScaleDown().getIdleTime());
        assertEquals(Boolean.TRUE, serverGroupsApi.lastCreateRequest.getScaling().getScaleDown().getIgnorePlayers());
        assertEquals(List.of("internal/setup"), serverGroupsApi.lastCreateRequest.getWorkflows().getWhen().getStart());
        assertEquals(List.of("internal/cleanup"), serverGroupsApi.lastCreateRequest.getWorkflows().getWhen().getStop());
        assertEquals(List.of("default/backup", "default/copy-to-template"), serverGroupsApi.lastCreateRequest.getWorkflows().getManual());
    }

    @Test
    void createGroup_partialScaling_appliesDefaultsForUnsetPrimitiveFields() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        ScaleDownConfig scaleDown = ScaleDownConfig.builder()
                .idleTime("5m")
                .build();

        ScalingConfig scaling = ScalingConfig.builder()
                .minServers(1)
                .scaleDown(scaleDown)
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .scaling(scaling)
                .build();

        api.createGroup(request).join();

        assertEquals(BigDecimal.valueOf(0.75), serverGroupsApi.lastCreateRequest.getScaling().getPlayerThreshold());
        assertEquals(Boolean.TRUE, serverGroupsApi.lastCreateRequest.getScaling().getScaleDown().getIgnorePlayers());
        assertEquals("5m", serverGroupsApi.lastCreateRequest.getScaling().getScaleDown().getIdleTime());
    }

    @Test
    void createGroup_inlineBlueprint_nonJavaRuntimeDoesNotReceiveJavaDefaults() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        RuntimeConfig runtimeConfig = RuntimeConfig.builder()
                .type(RuntimeType.NODE)
                .with(Map.of("entrypoint", "server.js"))
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder()
                        .runtimeConfig(runtimeConfig)
                        .build())
                .build();

        api.createGroup(request).join();

        assertEquals("node", blueprintsApi.lastCreateRequest.getRuntimeConfig().getType());
        assertEquals(Map.of("entrypoint", "server.js"), blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith());
        assertFalse(blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().containsKey("version"));
        assertFalse(blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().containsKey("options"));
        assertFalse(blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().containsKey("args"));
    }

    @Test
    void createGroup_invalidPlayerThresholdFailsBeforeBlueprintCreation() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        ScalingConfig scaling = ScalingConfig.builder()
                .playerThreshold(75)
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .scaling(scaling)
                .createBlueprint(CreateBlueprintRequest.builder().configurator("paper").build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(0, blueprintsApi.postCalls);
        assertEquals(0, blueprintsApi.deleteCalls);
        assertEquals(0, serverGroupsApi.postCalls);
    }

    @Test
    void createGroup_httpFailure_rollsBackInlineBlueprint() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        serverGroupsApi.postFailure = new ApiException(400, "bad request");

        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals(List.of("bp-1"), blueprintsApi.deletedBlueprintIds);
        assertEquals(0, serverGroupsApi.getCalls, "HTTP failures should roll back directly without reconciliation");
    }

    @Test
    void createGroup_httpFailure_retriesAndSurfacesRollbackFailure() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        serverGroupsApi.postFailure = new ApiException(400, "bad request");
        blueprintsApi.deleteFailure = new ApiException(500, "delete failed");

        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder().configurator("paper").build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("rollback inline blueprint"));
        assertInstanceOf(ApiException.class, failure.getCause().getCause());
        assertEquals(3, blueprintsApi.deleteCalls);
    }

    @Test
    void createGroup_transportFailure_keepsBlueprintWhenGroupAlreadyExists() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        serverGroupsApi.postFailure = new ApiException("timeout", new IOException("timeout"), 0, null, null);
        serverGroupsApi.listResponse = listResponseWithGroup("Lobby", "bp-1");

        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertEquals(1, serverGroupsApi.getCalls);
        assertEquals(List.of(), blueprintsApi.deletedBlueprintIds);
    }

    @Test
    void createGroup_http5xx_rollsBackBlueprintWhenReconciliationFindsNoGroup() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);
        serverGroupsApi.postFailure = new ApiException(500, "controller unavailable");

        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals(3, serverGroupsApi.getCalls);
        assertEquals(List.of("bp-1"), blueprintsApi.deletedBlueprintIds);
    }

    @Test
    void createGroup_inlineBlueprintRejectsConflictingSourceImage() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakeServerGroupsApi serverGroupsApi = new FakeServerGroupsApi(order);

        GroupApiImpl api = new GroupApiImpl(options(), new NoOpQueryCache(), serverGroupsApi, blueprintsApi);

        SourceConfig source = SourceConfig.builder()
                .image("ghcr.io/example/server:latest")
                .build();

        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("Lobby")
                .source(source)
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createGroup(request).join());

        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals(0, blueprintsApi.postCalls);
        assertEquals(0, serverGroupsApi.postCalls);
    }

    private static CloudApiOptions options() {
        return CloudApiOptions.builder()
                .controllerUrl("http://localhost")
                .networkId("test-network")
                .networkSecret("test-secret")
                .disableCache()
                .build();
    }

    private static ModelsListServerGroupsResponse listResponseWithGroup(String name, String blueprintId) {
        ModelsSourceConfig source = new ModelsSourceConfig();
        source.setType("blueprint");
        source.setBlueprint(blueprintId);

        ModelsServerGroupSummary summary = new ModelsServerGroupSummary();
        summary.setServerGroupId("group-1");
        summary.setName(name);
        summary.setType(GroupServerType.SERVER.name());
        summary.setSource(source);
        summary.setCreatedAt("2026-04-22T00:00:00Z");
        summary.setUpdatedAt("2026-04-22T00:00:00Z");

        ModelsListServerGroupsResponse response = new ModelsListServerGroupsResponse();
        response.setServerGroups(List.of(summary));
        return response;
    }

    private static final class FakeBlueprintsApi extends BlueprintsApi {
        private final AtomicInteger order;
        private int postCalls;
        private int postOrder;
        private ModelsCreateBlueprintRequest lastCreateRequest;
        private int deleteCalls;
        private ApiException deleteFailure;
        private final List<String> deletedBlueprintIds = new ArrayList<>();

        private FakeBlueprintsApi(AtomicInteger order) {
            this.order = order;
        }

        @Override
        public ModelsCreateBlueprintResponse v0BlueprintsPost(String xNetworkID,
                                                              String xNetworkSecret,
                                                              V0BlueprintsPostRequest request) {
            postCalls++;
            postOrder = order.incrementAndGet();
            lastCreateRequest = request.getModelsCreateBlueprintRequest();

            ModelsCreateBlueprintResponse response = new ModelsCreateBlueprintResponse();
            response.setBlueprintId("bp-1");
            response.setName(lastCreateRequest.getName());
            response.setConfigurator(lastCreateRequest.getConfigurator());
            response.setRuntimeConfig(lastCreateRequest.getRuntimeConfig());
            response.setWorkflowSteps(lastCreateRequest.getWorkflowSteps());
            response.setCreatedAt("2026-04-22T00:00:00Z");
            response.setUpdatedAt("2026-04-22T00:00:00Z");
            return response;
        }

        @Override
        public app.simplecloud.api.web.models.ModelsDeleteBlueprintResponse v0BlueprintsDelete(String xNetworkID,
                                                                                                String xNetworkSecret,
                                                                                                String blueprintId) throws ApiException {
            deleteCalls++;
            deletedBlueprintIds.add(blueprintId);
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            return new app.simplecloud.api.web.models.ModelsDeleteBlueprintResponse();
        }
    }

    private static final class FakeServerGroupsApi extends ServerGroupsApi {
        private final AtomicInteger order;
        private int postCalls;
        private int postOrder;
        private int getCalls;
        private ModelsCreateServerGroupRequest lastCreateRequest;
        private ApiException postFailure;
        private ModelsListServerGroupsResponse listResponse = new ModelsListServerGroupsResponse();

        private FakeServerGroupsApi(AtomicInteger order) {
            this.order = order;
        }

        @Override
        public ModelsCreateServerGroupResponse v0ServerGroupsPost(String xNetworkID,
                                                                  String xNetworkSecret,
                                                                  V0ServerGroupsPostRequest request) throws ApiException {
            postCalls++;
            postOrder = order.incrementAndGet();
            lastCreateRequest = request.getModelsCreateServerGroupRequest();
            if (postFailure != null) {
                throw postFailure;
            }

            ModelsCreateServerGroupResponse response = new ModelsCreateServerGroupResponse();
            response.setServerGroupId("group-1");
            response.setName(lastCreateRequest.getName());
            response.setType(lastCreateRequest.getType());
            response.setMinMemory(lastCreateRequest.getMinMemory());
            response.setMaxMemory(lastCreateRequest.getMaxMemory());
            response.setMaxPlayers(lastCreateRequest.getMaxPlayers());
            response.setActive(lastCreateRequest.getActive());
            response.setPriority(lastCreateRequest.getPriority());
            response.setDeployment(lastCreateRequest.getDeployment());
            response.setScaling(lastCreateRequest.getScaling());
            response.setSource(lastCreateRequest.getSource());
            response.setWorkflows(lastCreateRequest.getWorkflows());
            response.setProperties(lastCreateRequest.getProperties());
            response.setTags(lastCreateRequest.getTags());
            response.setCreatedAt("2026-04-22T00:00:00Z");
            response.setUpdatedAt("2026-04-22T00:00:00Z");
            return response;
        }

        @Override
        public ModelsListServerGroupsResponse v0ServerGroupsGet(String xNetworkID,
                                                                String xNetworkSecret,
                                                                Boolean active) {
            getCalls++;
            return listResponse;
        }
    }
}
