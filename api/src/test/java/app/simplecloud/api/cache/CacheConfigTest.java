package app.simplecloud.api.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheConfigTest {

    private static final Duration WINDOW = Duration.ofMillis(250);

    @Test
    void eventBatchWindow_setsTheWindow() {
        CacheConfig config = CacheConfig.builder()
                .eventBatchWindow(WINDOW)
                .build();

        assertEquals(WINDOW, config.getEventBatchWindow());
    }

    // The deprecated names are what this test checks.
    @SuppressWarnings("deprecation")
    @Test
    void deprecatedEventDebounceTime_stillSetsAndReadsTheWindow() {
        CacheConfig config = CacheConfig.builder()
                .eventDebounceTime(WINDOW)
                .build();

        assertEquals(WINDOW, config.getEventBatchWindow());
        assertEquals(WINDOW, config.getEventDebounceTime());
    }
}
