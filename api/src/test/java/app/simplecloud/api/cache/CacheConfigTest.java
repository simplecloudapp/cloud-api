package app.simplecloud.api.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheConfigTest {

    @Test
    void defaultConfig_usesDocumentedStaleTimes() {
        CacheConfig config = CacheConfig.DEFAULT;

        assertEquals(Duration.ofSeconds(3), config.getEntityConfigs().get(EntityType.SERVER).getStaleTime());
        assertEquals(Duration.ofSeconds(10), config.getEntityConfigs().get(EntityType.GROUP).getStaleTime());
        assertEquals(Duration.ofSeconds(10), config.getEntityConfigs().get(EntityType.PERSISTENT_SERVER).getStaleTime());
        assertEquals(Duration.ofSeconds(5), config.getEntityConfigs().get(EntityType.PLAYER).getStaleTime());
    }
}
