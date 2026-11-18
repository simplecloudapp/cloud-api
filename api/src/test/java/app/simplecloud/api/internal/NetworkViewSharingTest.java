package app.simplecloud.api.internal;

import app.simplecloud.api.CloudApi;
import app.simplecloud.api.CloudApiOptions;
import app.simplecloud.api.internal.networkstate.NetworkViewShell;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkViewSharingTest {

    private static final String UNREACHABLE_URL = "127.0.0.1:1";

    // Each test gets its own network, so an instance a failed test leaves open shares no view with the next.
    private final String networkId = UUID.randomUUID().toString();

    @Test
    void instancesWithOneNetworkControllerAndCredentialShareOneView() throws ReflectiveOperationException {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiOptions otherCredential = options(builder -> builder.networkSecret("otherPassword"));
        CloudApiOptions otherController = options(builder -> builder.controllerUrl("http://127.0.0.2:1"));

        try (CloudApiImpl first = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl second = (CloudApiImpl) CloudApi.create(sharedOptions);
             CloudApiImpl withOtherCredential = (CloudApiImpl) CloudApi.create(otherCredential);
             CloudApiImpl withOtherController = (CloudApiImpl) CloudApi.create(otherController)) {
            assertSame(view(first), view(second));
            assertNotSame(view(first), view(withOtherCredential));
            assertNotSame(view(first), view(withOtherController));
        }
    }

    @Test
    void aCredentialThatMayBeAJwtAndANetworkThatIsNoUuidGetNoView() throws ReflectiveOperationException {
        CloudApiOptions jwt = options(builder -> builder.networkSecret("header.payload.signature"));
        CloudApiOptions jsonCredential = options(builder -> builder.networkSecret("{\"token\":\"abc\"}"));
        CloudApiOptions namedNetwork = options(builder -> builder.networkId("default"));

        try (CloudApiImpl withJwt = (CloudApiImpl) CloudApi.create(jwt);
             CloudApiImpl withJsonCredential = (CloudApiImpl) CloudApi.create(jsonCredential);
             CloudApiImpl withNamedNetwork = (CloudApiImpl) CloudApi.create(namedNetwork)) {
            assertNull(viewHandle(withJwt));
            assertNull(viewHandle(withJsonCredential));
            assertNull(viewHandle(withNamedNetwork));
        }
    }

    @Test
    void theViewStaysOpenUntilTheLastInstanceCloses() throws ReflectiveOperationException {
        CloudApiOptions sharedOptions = options(UnaryOperator.identity());
        CloudApiImpl platformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        CloudApiImpl otherPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        SharedNetworkView sharedView = view(platformPlugin);

        platformPlugin.close();
        CloudApiImpl reenabledPlatformPlugin = (CloudApiImpl) CloudApi.create(sharedOptions);
        boolean reenabledSharesTheView = sharedView == view(reenabledPlatformPlugin);
        boolean stoppedWhileInUse = isStopped(sharedView.shell());
        otherPlugin.close();
        reenabledPlatformPlugin.close();

        assertTrue(reenabledSharesTheView);
        assertFalse(stoppedWhileInUse);
        assertTrue(isStopped(sharedView.shell()), "the last close closes the view");
        try (CloudApiImpl afterRestart = (CloudApiImpl) CloudApi.create(sharedOptions)) {
            assertNotSame(sharedView, view(afterRestart));
        }
    }

    private CloudApiOptions options(UnaryOperator<CloudApiOptions.Builder> customizer) {
        CloudApiOptions.Builder builder = CloudApiOptions.builder()
                .natsUrl("nats://" + UNREACHABLE_URL)
                .controllerUrl("http://" + UNREACHABLE_URL)
                .networkId(networkId)
                .networkSecret("networkPassword");
        return customizer.apply(builder).build();
    }

    private static SharedNetworkView view(CloudApiImpl api) throws ReflectiveOperationException {
        return viewHandle(api).resource();
    }

    // The field is declared with this type; reading it by reflection drops the type argument.
    @SuppressWarnings("unchecked")
    private static SharedResources.Handle<SharedNetworkView> viewHandle(CloudApiImpl api)
            throws ReflectiveOperationException {
        Field field = CloudApiImpl.class.getDeclaredField("networkViewHandle");
        field.setAccessible(true);
        return (SharedResources.Handle<SharedNetworkView>) field.get(api);
    }

    private static boolean isStopped(NetworkViewShell shell) throws ReflectiveOperationException {
        Field field = NetworkViewShell.class.getDeclaredField("stopped");
        field.setAccessible(true);
        return field.getBoolean(shell);
    }
}
