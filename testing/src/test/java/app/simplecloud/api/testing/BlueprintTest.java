package app.simplecloud.api.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.simplecloud.api.CloudApiException;
import app.simplecloud.api.blueprint.Blueprint;
import app.simplecloud.api.blueprint.CreateBlueprintRequest;
import app.simplecloud.api.blueprint.RuntimeConfig;
import app.simplecloud.api.blueprint.RuntimeType;
import app.simplecloud.api.blueprint.UpdateBlueprintRequest;
import app.simplecloud.api.event.blueprint.BlueprintCreatedEvent;
import app.simplecloud.api.event.blueprint.BlueprintDeletedEvent;
import app.simplecloud.api.event.blueprint.BlueprintUpdatedEvent;
import app.simplecloud.api.group.CreateGroupRequest;
import app.simplecloud.api.group.SourceConfig;
import app.simplecloud.api.group.SourceType;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.testing.group.TestGroup;
import app.simplecloud.api.testing.server.TestServer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

@CloudTest
class BlueprintTest {

    @Test
    void createdBlueprintIsVisibleThroughApi(TestCloud cloud) {
        var created = cloud.track(BlueprintCreatedEvent.class);

        Blueprint paper = cloud.createBlueprint("paper");

        Blueprint fetched = cloud.api().blueprint().getBlueprintByName("paper").join();
        assertEquals(paper.getBlueprintId(), fetched.getBlueprintId());
        assertEquals("1.21.4", fetched.getMinecraftVersion());
        assertNotNull(fetched.getServerUrl());
        assertEquals(RuntimeType.JAVA, fetched.getRuntimeConfig().type());
        assertEquals(List.of("internal/setup"), fetched.getWorkflowSteps());
        assertEquals(paper.getBlueprintId(), created.single().getBlueprint().getBlueprintId());
        assertEquals(
                List.of(fetched.getBlueprintId()),
                cloud.api().blueprint().getAllBlueprints().join().stream()
                        .map(Blueprint::getBlueprintId)
                        .toList());
    }

    @Test
    void createWithoutRequiredFieldsFailsWithInvalidRequest(TestCloud cloud) {
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api()
                        .blueprint()
                        .createBlueprint(
                                CreateBlueprintRequest.builder().name("broken").build())
                        .join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.INVALID_REQUEST, cause.getReason());
        assertTrue(cloud.blueprints().isEmpty());
    }

    @Test
    void duplicateNameFailsWithConflict(TestCloud cloud) {
        cloud.createBlueprint("paper");

        CloudApiException exception = assertThrows(CloudApiException.class, () -> cloud.createBlueprint("paper"));

        assertEquals(CloudApiException.Reason.CONFLICT, exception.getReason());
    }

    @Test
    void updateChangesOnlyGivenFields(TestCloud cloud) {
        Blueprint paper = cloud.createBlueprint(CreateBlueprintRequest.builder()
                .name("paper")
                .configurator("paper")
                .serverSoftware("paper")
                .minecraftVersion("1.21.4")
                .softwareVersion("1.21.4")
                .workflowSteps(List.of("download"))
                .build());
        var updated = cloud.track(BlueprintUpdatedEvent.class);

        Blueprint result = cloud.api()
                .blueprint()
                .updateBlueprint(
                        paper.getBlueprintId(),
                        UpdateBlueprintRequest.builder()
                                .minecraftVersion("1.21.5")
                                .build())
                .join();

        assertEquals("1.21.5", result.getMinecraftVersion());
        assertEquals("1.21.4", result.getSoftwareVersion());
        assertEquals(List.of("download"), result.getWorkflowSteps());
        assertEquals("1.21.5", updated.single().getBlueprint().getMinecraftVersion());
    }

    @Test
    void blueprintInUseCannotBeDeleted(TestCloud cloud) {
        Blueprint paper = cloud.createBlueprint("paper");
        cloud.createGroup(CreateGroupRequest.builder()
                .name("lobby")
                .source(SourceConfig.builder()
                        .type(SourceType.BLUEPRINT)
                        .blueprint(paper.getBlueprintId())
                        .build())
                .build());

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api()
                        .blueprint()
                        .deleteBlueprint(paper.getBlueprintId())
                        .join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.CONFLICT, cause.getReason());
    }

    @Test
    void deleteRemovesUnusedBlueprint(TestCloud cloud) {
        Blueprint paper = cloud.createBlueprint("paper");
        var deleted = cloud.track(BlueprintDeletedEvent.class);

        cloud.api().blueprint().deleteBlueprint(paper.getBlueprintId()).join();

        assertTrue(cloud.blueprint("paper").isEmpty());
        assertEquals("paper", deleted.single().getName());
    }

    @Test
    void serverReturnsBlueprintOfItsGroup(TestCloud cloud) {
        Blueprint paper = cloud.createBlueprint("paper");
        TestServer lobby = cloud.createGroup(CreateGroupRequest.builder()
                        .name("lobby")
                        .source(SourceConfig.builder()
                                .type(SourceType.BLUEPRINT)
                                .blueprint(paper.getBlueprintId())
                                .build())
                        .build())
                .startServer();

        Server fetched = cloud.api().server().getServerById(lobby.getServerId()).join();

        assertEquals(paper.getBlueprintId(), fetched.getBlueprint().getBlueprintId());
        assertNull(cloud.createGroup("bedwars").startServer().getBlueprint());
    }

    @Test
    void inlineBlueprintIsCreatedAndDeletedWithGroup(TestCloud cloud) {
        TestGroup lobby = cloud.createGroup(CreateGroupRequest.builder()
                .name("lobby")
                .createBlueprint(CreateBlueprintRequest.builder()
                        .configurator("paper")
                        .serverSoftware("paper")
                        .minecraftVersion("1.21.4")
                        .softwareVersion("1.21.4")
                        .build())
                .build());

        Blueprint blueprint = cloud.blueprint("lobby").orElseThrow();
        assertEquals(blueprint.getBlueprintId(), lobby.getSource().blueprint());
        assertEquals(List.of("internal/setup"), blueprint.getWorkflowSteps());

        lobby.delete();

        assertFalse(cloud.blueprint("lobby").isPresent());
    }

    @Test
    void unknownSourceBlueprintFailsWithNotFound(TestCloud cloud) {
        CloudApiException exception = assertThrows(
                CloudApiException.class,
                () -> cloud.createGroup(CreateGroupRequest.builder()
                        .name("lobby")
                        .source(SourceConfig.builder()
                                .type(SourceType.BLUEPRINT)
                                .blueprint("unknown")
                                .build())
                        .build()));

        assertEquals(CloudApiException.Reason.NOT_FOUND, exception.getReason());
    }

    @Test
    void updateRuntimeConfigWithoutTypeFails(TestCloud cloud) {
        Blueprint paper = cloud.createBlueprint("paper");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> cloud.api()
                        .blueprint()
                        .updateBlueprint(
                                paper.getBlueprintId(),
                                UpdateBlueprintRequest.builder()
                                        .runtimeConfig(RuntimeConfig.builder()
                                                .with(Map.of("version", "17"))
                                                .build())
                                        .build())
                        .join());

        CloudApiException cause = assertInstanceOf(CloudApiException.class, exception.getCause());
        assertEquals(CloudApiException.Reason.INVALID_REQUEST, cause.getReason());
    }
}
