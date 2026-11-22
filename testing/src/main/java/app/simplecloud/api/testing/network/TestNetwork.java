package app.simplecloud.api.testing.network;

import java.time.Duration;

/**
 * Simulates network problems between your code and the controller. Only calls through
 * {@code TestCloud.api()} are affected, test objects like {@code TestServer} always work.
 *
 * <pre>{@code
 * cloud.network().failNext(ServerApi.class, "stopServer");
 * cloud.network().delay(Duration.ofMillis(200));
 * }</pre>
 *
 * <p>Failures default to a {@code CloudApiException} with reason {@code UNAVAILABLE}.
 */
public interface TestNetwork {

    /**
     * Fails the next call of the method.
     *
     * @throws IllegalArgumentException if the api has no such method
     */
    TestNetwork failNext(Class<?> api, String method);

    /**
     * Fails the next call of the method with the given error.
     */
    TestNetwork failNext(Class<?> api, String method, RuntimeException error);

    /**
     * Fails every call of the method until {@link #clear()}.
     */
    TestNetwork failAlways(Class<?> api, String method);

    /**
     * Fails every call of the method with the given error until {@link #clear()}.
     */
    TestNetwork failAlways(Class<?> api, String method, RuntimeException error);

    /**
     * Delays all async api calls.
     */
    TestNetwork delay(Duration delay);

    /**
     * Delays all calls of the method.
     */
    TestNetwork delay(Class<?> api, String method, Duration delay);

    /**
     * Removes all failures and delays.
     */
    void clear();
}
