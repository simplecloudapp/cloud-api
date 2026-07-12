package app.simplecloud.api.internal.persistentserver;

import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.internal.cache.NoOpQueryCache;
import app.simplecloud.api.persistentserver.CreatePersistentServerRequest;
import app.simplecloud.api.persistentserver.PersistentServer;
import app.simplecloud.api.web.ApiException;
import app.simplecloud.api.web.apis.BlueprintsApi;
import app.simplecloud.api.web.apis.PersistentServersApi;
import app.simplecloud.api.web.models.ModelsCreateBlueprintRequest;
import app.simplecloud.api.web.models.ModelsCreateBlueprintResponse;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerRequest;
import app.simplecloud.api.web.models.ModelsCreatePersistentServerResponse;
import app.simplecloud.api.web.models.ModelsListPersistentServersResponse;
import app.simplecloud.api.web.models.ModelsPersistentServerSummary;
import app.simplecloud.api.web.models.ModelsSourceConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentServerApiImplTest {

    @Test
    void getPersistentServerById_usesControllerIdFilter() {
        AtomicInteger order = new AtomicInteger();
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, new FakeBlueprintsApi(order));

        api.getPersistentServerById("persistent-1").join();

        assertEquals("persistent-1", persistentServersApi.lastPersistentServerId);
        assertEquals(null, persistentServersApi.lastName);
    }

    @Test
    void getPersistentServerByName_usesControllerNameFilter() {
        AtomicInteger order = new AtomicInteger();
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, new FakeBlueprintsApi(order));

        api.getPersistentServerByName("Lobby-1").join();

        assertEquals(null, persistentServersApi.lastPersistentServerId);
        assertEquals("Lobby-1", persistentServersApi.lastName);
    }

    @Test
    void createPersistentServer_inlineBlueprint_usesCreateResponseWithoutRefetch() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, blueprintsApi);

        CreatePersistentServerRequest request = CreatePersistentServerRequest.builder()
                .name("Lobby-1")
                .serverhostId("host-1")
                .type(GroupServerType.SERVER)
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        PersistentServer persistentServer = api.createPersistentServer(request).join();

        assertEquals(1, blueprintsApi.postCalls);
        assertEquals(1, persistentServersApi.postCalls);
        assertTrue(blueprintsApi.postOrder < persistentServersApi.postOrder);
        assertEquals(0, persistentServersApi.getCalls, "successful create should not do a follow-up GET");
        assertEquals("Lobby-1", blueprintsApi.lastCreateRequest.getName());
        assertEquals("bp-1", persistentServersApi.lastCreateRequest.getSource().getBlueprint());
        assertEquals(ModelsSourceConfig.TypeEnum.BLUEPRINT, persistentServersApi.lastCreateRequest.getSource().getType());
        assertNotNull(persistentServer.getSource());
        assertEquals("bp-1", persistentServer.getSource().getBlueprint());
    }

    @Test
    void createPersistentServer_appliesDashboardDefaults() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, blueprintsApi);

        CreatePersistentServerRequest request = CreatePersistentServerRequest.builder()
                .name("Proxy-1")
                .createBlueprint(CreateBlueprintRequest.builder()
                        .configurator("velocity")
                        .serverSoftware("velocity")
                        .build())
                .build();

        api.createPersistentServer(request).join();

        assertEquals("Proxy-1", blueprintsApi.lastCreateRequest.getName());
        assertEquals(List.of("internal/setup"), blueprintsApi.lastCreateRequest.getWorkflowSteps());
        assertEquals("java", blueprintsApi.lastCreateRequest.getRuntimeConfig().getType());
        assertEquals("21", blueprintsApi.lastCreateRequest.getRuntimeConfig().getWith().get("version"));
        assertEquals(ModelsCreatePersistentServerRequest.TypeEnum.SERVER, persistentServersApi.lastCreateRequest.getType());
        assertEquals(Integer.valueOf(50), persistentServersApi.lastCreateRequest.getMaxPlayers());
        assertEquals(Boolean.TRUE, persistentServersApi.lastCreateRequest.getActive());
        assertEquals(List.of("Proxy-1"), persistentServersApi.lastCreateRequest.getTags());
        assertEquals(Map.of(), persistentServersApi.lastCreateRequest.getProperties());
        assertEquals(List.of("internal/setup"), persistentServersApi.lastCreateRequest.getWorkflows().getWhen().getStart());
        assertEquals(List.of("internal/cleanup"), persistentServersApi.lastCreateRequest.getWorkflows().getWhen().getStop());
        assertEquals(List.of("default/backup", "default/copy-to-template"), persistentServersApi.lastCreateRequest.getWorkflows().getManual());
    }

    @Test
    void createPersistentServer_inlineBlueprint_resolvesServerUrlFromManifest() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        PersistentServerApiImpl api = new PersistentServerApiImpl(
                options(),
                new NoOpQueryCache(),
                persistentServersApi,
                blueprintsApi,
                request -> {
                    if ("velocity".equals(request.getServerSoftware()) && "3.4.0-SNAPSHOT".equals(request.getSoftwareVersion())) {
                        return "https://example.com/velocity-3.4.0-SNAPSHOT.jar";
                    }
                    return null;
                }
        );

        CreatePersistentServerRequest request = CreatePersistentServerRequest.builder()
                .name("Proxy-1")
                .createBlueprint(CreateBlueprintRequest.builder()
                        .serverSoftware("velocity")
                        .softwareVersion("3.4.0-SNAPSHOT")
                        .build())
                .build();

        api.createPersistentServer(request).join();

        assertEquals("https://example.com/velocity-3.4.0-SNAPSHOT.jar", blueprintsApi.lastCreateRequest.getServerUrl());
    }

    @Test
    void createPersistentServer_httpFailure_rollsBackInlineBlueprint() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        persistentServersApi.postFailure = new ApiException(400, "bad request");

        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, blueprintsApi);
        CreatePersistentServerRequest request = CreatePersistentServerRequest.builder()
                .name("Lobby-1")
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createPersistentServer(request).join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals(List.of("bp-1"), blueprintsApi.deletedBlueprintIds);
        assertEquals(0, persistentServersApi.getCalls, "HTTP failures should roll back directly without reconciliation");
    }

    @Test
    void createPersistentServer_http5xx_rollsBackBlueprintWhenReconciliationFindsNoServer() {
        AtomicInteger order = new AtomicInteger();
        FakeBlueprintsApi blueprintsApi = new FakeBlueprintsApi(order);
        FakePersistentServersApi persistentServersApi = new FakePersistentServersApi(order);
        persistentServersApi.postFailure = new ApiException(500, "controller unavailable");

        PersistentServerApiImpl api = new PersistentServerApiImpl(options(), new NoOpQueryCache(), persistentServersApi, blueprintsApi);
        CreatePersistentServerRequest request = CreatePersistentServerRequest.builder()
                .name("Lobby-1")
                .createBlueprint(CreateBlueprintRequest.builder().build())
                .build();

        CompletionException failure = assertThrows(CompletionException.class, () -> api.createPersistentServer(request).join());

        assertInstanceOf(RuntimeException.class, failure.getCause());
        assertEquals(3, persistentServersApi.getCalls);
        assertEquals(List.of("bp-1"), blueprintsApi.deletedBlueprintIds);
    }

    private static CloudApiOptions options() {
        return CloudApiOptions.builder()
                .controllerUrl("http://localhost")
                .networkId("test-network")
                .networkSecret("test-secret")
                .disableCache()
                .build();
    }

    private static final class FakeBlueprintsApi extends BlueprintsApi {
        private final AtomicInteger order;
        private int postCalls;
        private int postOrder;
        private ModelsCreateBlueprintRequest lastCreateRequest;
        private final List<String> deletedBlueprintIds = new ArrayList<>();

        private FakeBlueprintsApi(AtomicInteger order) {
            this.order = order;
        }

        @Override
        public ModelsCreateBlueprintResponse v0BlueprintsPost(String xNetworkID,
                                                              String xNetworkSecret,
                                                              ModelsCreateBlueprintRequest request) {
            postCalls++;
            postOrder = order.incrementAndGet();
            lastCreateRequest = request;

            ModelsCreateBlueprintResponse response = new ModelsCreateBlueprintResponse();
            response.setBlueprintId("bp-1");
            response.setName(lastCreateRequest.getName());
            response.setCreatedAt("2026-04-22T00:00:00Z");
            response.setUpdatedAt("2026-04-22T00:00:00Z");
            return response;
        }

        @Override
        public app.simplecloud.api.web.models.ModelsDeleteBlueprintResponse v0BlueprintsDelete(String xNetworkID,
                                                                                                String xNetworkSecret,
                                                                                                String blueprintId) {
            deletedBlueprintIds.add(blueprintId);
            return new app.simplecloud.api.web.models.ModelsDeleteBlueprintResponse();
        }
    }

    private static final class FakePersistentServersApi extends PersistentServersApi {
        private final AtomicInteger order;
        private int postCalls;
        private int postOrder;
        private int getCalls;
        private String lastPersistentServerId;
        private String lastName;
        private ModelsCreatePersistentServerRequest lastCreateRequest;
        private ApiException postFailure;

        private FakePersistentServersApi(AtomicInteger order) {
            this.order = order;
        }

        @Override
        public ModelsCreatePersistentServerResponse v0PersistentServersPost(String xNetworkID,
                                                                            String xNetworkSecret,
                                                                            ModelsCreatePersistentServerRequest request) throws ApiException {
            postCalls++;
            postOrder = order.incrementAndGet();
            lastCreateRequest = request;
            if (postFailure != null) {
                throw postFailure;
            }

            ModelsCreatePersistentServerResponse response = new ModelsCreatePersistentServerResponse();
            response.setPersistentServerId("persistent-1");
            response.setName(lastCreateRequest.getName());
            response.setType(lastCreateRequest.getType().getValue());
            response.setMinMemory(lastCreateRequest.getMinMemory());
            response.setMaxMemory(lastCreateRequest.getMaxMemory());
            response.setMaxPlayers(lastCreateRequest.getMaxPlayers());
            response.setActive(lastCreateRequest.getActive());
            response.setPriority(lastCreateRequest.getPriority());
            response.setPlayerCount(0);
            response.setServerhostId(lastCreateRequest.getServerhostId());
            response.setSource(lastCreateRequest.getSource());
            response.setWorkflows(lastCreateRequest.getWorkflows());
            response.setProperties(lastCreateRequest.getProperties());
            response.setTags(lastCreateRequest.getTags());
            response.setCreatedAt("2026-04-22T00:00:00Z");
            response.setUpdatedAt("2026-04-22T00:00:00Z");
            return response;
        }

        @Override
        public ModelsListPersistentServersResponse v0PersistentServersGet(String xNetworkID,
                                                                          String xNetworkSecret,
                                                                          String persistentServerId,
                                                                          String name,
                                                                          String type,
                                                                          String tags,
                                                                          Boolean active,
                                                                          String sourceType,
                                                                          String blueprintId,
                                                                          String serverhostId,
                                                                          Integer limit,
                                                                          Integer offset) {
            getCalls++;
            lastPersistentServerId = persistentServerId;
            lastName = name;
            ModelsPersistentServerSummary summary = new ModelsPersistentServerSummary();
            summary.setPersistentServerId("persistent-1");
            summary.setName("Lobby-1");
            summary.setSource(new ModelsSourceConfig());

            ModelsListPersistentServersResponse response = new ModelsListPersistentServersResponse();
            response.setPersistentServers(List.of(summary));
            return response;
        }
    }
}
