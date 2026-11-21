package app.simplecloud.api.testing.event;

import java.time.Duration;
import java.util.List;

public interface EventCollector<E> extends AutoCloseable {

    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    List<E> events();

    int count();

    E first();

    E last();

    E single();

    void assertEmpty();

    default E awaitFirst() {
        return awaitFirst(DEFAULT_TIMEOUT);
    }

    E awaitFirst(Duration timeout);

    default List<E> await(int count) {
        return await(count, DEFAULT_TIMEOUT);
    }

    List<E> await(int count, Duration timeout);

    void clear();

    @Override
    void close();
}
