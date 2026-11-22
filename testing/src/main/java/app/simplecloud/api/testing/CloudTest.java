package app.simplecloud.api.testing;

import app.simplecloud.api.testing.internal.CloudTestExtension;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enables the cloud test extension. Every test method gets a fresh {@link TestCloud} as parameter,
 * which is closed after the test.
 *
 * <pre>{@code
 * @CloudTest
 * class LobbyTest {
 *
 *     @Test
 *     void startsLobby(TestCloud cloud) {
 *         TestServer lobby = cloud.createGroup("lobby").startServer();
 *         assertEquals(ServerState.AVAILABLE, lobby.getState());
 *     }
 * }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(CloudTestExtension.class)
public @interface CloudTest {}
