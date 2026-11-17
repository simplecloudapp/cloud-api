package app.simplecloud.api.internal.networkstate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSequenceTest {

    private static final int DRAWS = 10_000;

    @Test
    void seeded_sameSeedAndIndex_returnsSameNumber() {
        RandomSequence first = RandomSequence.seeded(42);
        RandomSequence second = RandomSequence.seeded(42);

        for (long index = 0; index < DRAWS; index++) {
            assertEquals(first.at(index), second.at(index));
        }
    }

    @Test
    void seeded_returnsNumbersInUnitRangeSpreadOverIt() {
        RandomSequence sequence = RandomSequence.seeded(7);
        int[] countPerTenth = new int[10];

        for (long index = 0; index < DRAWS; index++) {
            double number = sequence.at(index);
            assertTrue(number >= 0 && number < 1, "number " + number + " at index " + index);
            int tenth = (int) (number * 10);
            countPerTenth[tenth]++;
        }

        for (int count : countPerTenth) {
            assertTrue(count > DRAWS / 10 * 0.9 && count < DRAWS / 10 * 1.1, "tenth drawn " + count + " times");
        }
    }

    @Test
    void seeded_differentSeeds_returnDifferentNumbers() {
        Set<Double> firstNumbers = new HashSet<>();
        for (long seed = 0; seed < 100; seed++) {
            firstNumbers.add(RandomSequence.seeded(seed).at(0));
        }

        assertEquals(100, firstNumbers.size());
        assertNotEquals(RandomSequence.seeded(1).at(5), RandomSequence.seeded(2).at(5));
    }
}
