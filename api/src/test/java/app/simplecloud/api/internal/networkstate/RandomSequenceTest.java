package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSequenceTest {

    private static final int DRAWS = 10_000;
    private static final long SEED = 42;
    private static final long OTHER_SEED = 7;
    private static final int TENTHS = 10;
    /**
     * Each tenth of [0, 1) holds its fair share of the draws within this fraction of it.
     */
    private static final double FAIR_SHARE_TOLERANCE = 0.1;
    private static final int SEEDS_COMPARED = 100;
    private static final long INDEX = 5;

    @Test
    void seeded_sameSeedAndIndex_returnsSameNumber() {
        RandomSequence first = RandomSequence.seeded(SEED);
        RandomSequence second = RandomSequence.seeded(SEED);

        for (long index = 0; index < DRAWS; index++) {
            assertEquals(first.at(index), second.at(index));
        }
    }

    @Test
    void seeded_returnsNumbersInUnitRangeSpreadOverIt() {
        RandomSequence sequence = RandomSequence.seeded(OTHER_SEED);
        int[] countPerTenth = new int[TENTHS];

        for (long index = 0; index < DRAWS; index++) {
            double number = sequence.at(index);
            assertTrue(number >= 0 && number < 1, "number " + number + " at index " + index);
            int tenth = (int) (number * TENTHS);
            countPerTenth[tenth]++;
        }

        int fairShare = DRAWS / TENTHS;
        double fewestAllowed = fairShare * (1 - FAIR_SHARE_TOLERANCE);
        double mostAllowed = fairShare * (1 + FAIR_SHARE_TOLERANCE);
        for (int count : countPerTenth) {
            assertTrue(count > fewestAllowed && count < mostAllowed, "tenth drawn " + count + " times");
        }
    }

    @Test
    void seeded_differentSeeds_returnDifferentNumbers() {
        Set<Double> firstNumbers = new HashSet<>();
        for (long seed = 0; seed < SEEDS_COMPARED; seed++) {
            firstNumbers.add(RandomSequence.seeded(seed).at(0));
        }

        assertEquals(SEEDS_COMPARED, firstNumbers.size());
        assertNotEquals(RandomSequence.seeded(SEED).at(INDEX), RandomSequence.seeded(OTHER_SEED).at(INDEX));
    }
}
