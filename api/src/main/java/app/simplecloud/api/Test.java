package app.simplecloud.api;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.event.Subscription;
import app.simplecloud.api.group.GroupServerType;
import app.simplecloud.api.server.ServerQuery;
import app.simplecloud.api.server.ServerState;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        CloudApi cloudApi = CloudApi.create(CloudApiOptions.builder()
                .networkId("69d16f48-a745-4cf7-8a88-152d68461981")
                .networkSecret("m5Hr1eLIY5BUOmeR7eiXJVPZnhlPkvUr")
                .build());

        cloudApi.server().getAllServers(ServerQuery.create()
                .filterByState(ServerState.AVAILABLE)
                .filterByServerGroupType(GroupServerType.SERVER)
        ).thenAccept(servers -> {
            System.out.println(servers.size());
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return null;
        });

        // Subscribe to group events
        Subscription groupCreatedSub = cloudApi.event().group().onCreated(event -> {
            System.out.println("Server group created:");
            System.out.println("  Network ID: " + event.getNetworkId());
            System.out.println("  Server Group ID: " + event.getServerGroupId());
            if (event.getGroup() != null) {
                System.out.println("  Name: " + event.getGroup().getName());
            }
            System.out.println("  Timestamp: " + event.getTimestamp());
        });

        Subscription groupUpdatedSub = cloudApi.event().group().onUpdated(event -> {
            if (event.getGroup() != null) {
                System.out.println("Server group updated: " + event.getGroup().getName());
            } else {
                System.out.println("Server group updated: " + event.getServerGroupId());
            }
        });

        Subscription groupDeletedSub = cloudApi.event().group().onDeleted(event -> {
            System.out.println("Server group deleted: " + event.getName());
            System.out.println("  Server Group ID: " + event.getServerGroupId());
        });

        // Subscribe to server events
        Subscription serverStartedSub = cloudApi.event().server().onStarted(event -> {
            System.out.println("Server started:");
            System.out.println("  Server ID: " + event.getServerId());
            System.out.println("  Server Group ID: " + event.getServerGroupId());
            if (event.getServer() != null) {
                System.out.println("  Address: " + event.getServer().getIp() + ":" + event.getServer().getPort());
                System.out.println("  State: " + event.getServer().getState());
                System.out.println("  Memory: " + event.getServer().getMinMemory() + "-" + event.getServer().getMaxMemory() + " MB");
            }
            System.out.println("  Timestamp: " + event.getTimestamp());
        });

        Subscription serverStoppedSub = cloudApi.event().server().onStopped(event -> {
            System.out.println("Server stopped:");
            System.out.println("  Server ID: " + event.getServerId());
            System.out.println("  Crashed: " + event.getCrashed());
            System.out.println("  Exit Code: " + event.getExitCode());
            System.out.println("  Reason: " + event.getReason());
            if (event.getCrashed()) {
                System.err.println("  WARNING: Server crashed!");
            }
        });

        Subscription serverStateChangedSub = cloudApi.event().server().onStateChanged(event -> {
            System.out.println("Server state changed:");
            System.out.println("  Server ID: " + event.getServerId());
            System.out.println("  Old State: " + event.getOldState());
            System.out.println("  New State: " + event.getNewState());
            if (event.getServer() != null) {
                System.out.println("  Address: " + event.getServer().getIp() + ":" + event.getServer().getPort());
                System.out.println("  NUM: " + event.getServer().getNumericalId());
            }
        });

        Subscription serverDeletedSub = cloudApi.event().server().onDeleted(event -> {
            System.out.println("Server deleted: " + event.getServerId());
        });

        // Subscribe to persistent server events
        Subscription persistentServerCreatedSub = cloudApi.event().persistentServer().onCreated(event -> {
            System.out.println("Persistent server created:");
            System.out.println("  Persistent Server ID: " + event.getPersistentServerId());
            System.out.println("  Name: " + event.getName());
        });

        Subscription persistentServerStartedSub = cloudApi.event().persistentServer().onStarted(event -> {
            System.out.println("Persistent server started:");
            System.out.println("  Persistent Server ID: " + event.getPersistentServerId());
            System.out.println("  Server ID: " + event.getServerId());
        });

        Subscription persistentServerStoppedSub = cloudApi.event().persistentServer().onStopped(event -> {
            System.out.println("Persistent server stopped:");
            System.out.println("  Persistent Server ID: " + event.getPersistentServerId());
            System.out.println("  Server ID: " + event.getServerId());
        });

        // Subscribe to blueprint events
        Subscription blueprintCreatedSub = cloudApi.event().blueprint().onCreated(event -> {
            System.out.println("Blueprint created:");
            System.out.println("  Blueprint ID: " + event.getBlueprintId());
            if (event.getBlueprint() != null) {
                System.out.println("  Name: " + event.getBlueprint().getName());
            }
        });

        Subscription blueprintUpdatedSub = cloudApi.event().blueprint().onUpdated(event -> {
            if (event.getBlueprint() != null) {
                System.out.println("Blueprint updated: " + event.getBlueprint().getName());
            } else {
                System.out.println("Blueprint updated: " + event.getBlueprintId());
            }
        });

        Subscription blueprintDeletedSub = cloudApi.event().blueprint().onDeleted(event -> {
            System.out.println("Blueprint deleted: " + event.getName());
        });

        System.out.println("Event listeners registered. Waiting for events...");
        System.out.println("Press Ctrl+C to exit.");

        // Subscriptions are long-lived and will remain active until explicitly unsubscribed
        // Keep the application running to receive events
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("Shutting down...");

            // Unsubscribe from all events
            groupCreatedSub.unsubscribe();
            groupUpdatedSub.unsubscribe();
            groupDeletedSub.unsubscribe();
            serverStartedSub.unsubscribe();
            serverStoppedSub.unsubscribe();
            serverStateChangedSub.unsubscribe();
            serverDeletedSub.unsubscribe();
            persistentServerCreatedSub.unsubscribe();
            persistentServerStartedSub.unsubscribe();
            persistentServerStoppedSub.unsubscribe();
            blueprintCreatedSub.unsubscribe();
            blueprintUpdatedSub.unsubscribe();
            blueprintDeletedSub.unsubscribe();

            System.out.println("All subscriptions closed.");
        }
    }

}
