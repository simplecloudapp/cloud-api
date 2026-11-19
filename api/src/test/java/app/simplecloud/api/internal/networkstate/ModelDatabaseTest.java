package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDatabaseTest {

    private static final int SEEDS = 50;
    private static final int WRITES_BEFORE_FAILOVER = 5;

    @Test
    void failOver_secondFailover_neverPromotesAStateOfTheOldPrimary() {
        for (long seed = 0; seed < SEEDS; seed++) {
            ModelDatabase database = new ModelDatabase(new Random(seed));
            for (int write = 0; write < WRITES_BEFORE_FAILOVER; write++) {
                database.commitRandomWrite();
            }

            database.failOver();
            long headAfterFirstFailover = database.current().headRevision();
            database.failOver();
            long headAfterSecondFailover = database.current().headRevision();

            assertTrue(headAfterSecondFailover <= headAfterFirstFailover,
                    "seed " + seed + ": the second failover went from " + headAfterFirstFailover + " to "
                            + headAfterSecondFailover + ", a head only the old primary had");
        }
    }
}
