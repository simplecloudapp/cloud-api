package app.simplecloud.api.internal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilTest {

    @Test
    void isUuidAcceptsUuidsInAnyCase() {
        assertTrue(ValidationUtil.isUuid("3c9a1f2e-6b4d-4e8a-9f1c-2d7e5b8a0c34"));
        assertTrue(ValidationUtil.isUuid("3C9A1F2E-6B4D-4E8A-9F1C-2D7E5B8A0C34"));
    }

    @Test
    void isUuidRejectsEverythingElse() {
        assertFalse(ValidationUtil.isUuid(null));
        assertFalse(ValidationUtil.isUuid(""));
        assertFalse(ValidationUtil.isUuid("lobby-1"));
        assertFalse(ValidationUtil.isUuid("3c9a1f2e6b4d4e8a9f1c2d7e5b8a0c34"));
        assertFalse(ValidationUtil.isUuid(" 3c9a1f2e-6b4d-4e8a-9f1c-2d7e5b8a0c34"));
    }

    @Test
    void isPlayerNameLimitsUtf8Bytes() {
        assertTrue(ValidationUtil.isPlayerName("Steve"));
        assertTrue(ValidationUtil.isPlayerName("a".repeat(16)));
        assertFalse(ValidationUtil.isPlayerName("a".repeat(17)));
        assertFalse(ValidationUtil.isPlayerName("ä".repeat(9)));
        assertFalse(ValidationUtil.isPlayerName(""));
        assertFalse(ValidationUtil.isPlayerName(null));
    }
}
