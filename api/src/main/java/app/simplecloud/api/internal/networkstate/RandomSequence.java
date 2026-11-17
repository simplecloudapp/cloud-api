package app.simplecloud.api.internal.networkstate;

/**
 * A fixed sequence of random numbers, so that a view stays a pure value: the same view and the same input
 * always give the same step. A view counts the numbers it has drawn and asks for the next index.
 */
@FunctionalInterface
public interface RandomSequence {

    /**
     * Returns the number at {@code index}, in [0, 1). The same index always returns the same number.
     */
    double at(long index);

    /**
     * Returns a sequence that is spread evenly over [0, 1) and differs per seed. The shell seeds it once per
     * view, for example from {@code System.nanoTime()}, so that processes do not draw the same delays.
     */
    static RandomSequence seeded(long seed) {
        return index -> seededNumberAt(seed, index);
    }

    // SplitMix64 (Steele, Lea and Flood, 2014): its mixing step turns consecutive inputs into independent
    // looking 64-bit values, so a number can be computed from its index alone. The top 53 bits fill a
    // double's mantissa, which gives an even spread over [0, 1).
    private static double seededNumberAt(long seed, long index) {
        long mixed = seed + index * 0x9e3779b97f4a7c15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed = mixed ^ (mixed >>> 31);
        long top53Bits = mixed >>> 11;
        return top53Bits * 0x1.0p-53;
    }
}
