package app.simplecloud.api.testing.network;

import java.time.Duration;

public interface TestNetwork {

    TestNetwork failNext(Class<?> api, String method);

    TestNetwork failNext(Class<?> api, String method, RuntimeException error);

    TestNetwork failAlways(Class<?> api, String method);

    TestNetwork failAlways(Class<?> api, String method, RuntimeException error);

    TestNetwork delay(Duration delay);

    TestNetwork delay(Class<?> api, String method, Duration delay);

    void clear();
}
