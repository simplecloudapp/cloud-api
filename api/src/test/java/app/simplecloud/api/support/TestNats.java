package app.simplecloud.api.support;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Nats;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class TestNats {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private TestNats() {
    }

    public static String url() {
        String url = System.getProperty("simplecloud.test.natsUrl");
        if (url != null && !url.isBlank()) {
            return url;
        }
        return NatsContainer.url();
    }

    public static Connection connect() throws Exception {
        Connection connection = Nats.connect(url());
        connection.flush(TIMEOUT);
        return connection;
    }

    public static String randomNetworkId() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static <T> T next(BlockingQueue<T> queue) throws InterruptedException {
        T value = queue.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        assertNotNull(value, "nothing received within " + TIMEOUT);
        return value;
    }

    public static Message request(Connection connection, String subject, byte[] data) throws Exception {
        Message response = connection.request(subject, data, TIMEOUT);
        assertNotNull(response, "no reply on " + subject);
        return response;
    }
}
