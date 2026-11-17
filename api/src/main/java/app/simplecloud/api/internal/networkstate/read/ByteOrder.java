package app.simplecloud.api.internal.networkstate.read;

import com.google.protobuf.ByteString;

import java.util.Comparator;

/**
 * The orders the controller sorts by. Postgres compares a uuid by its 16 bytes, where Java's
 * {@link java.util.UUID#compareTo} compares signed halves. Text compares by its UTF-8 bytes, as Postgres does
 * under collation {@code C} and as Go's JSON encoder sorts map keys, where {@link String#compareTo} compares
 * UTF-16 units.
 */
final class ByteOrder {

    static final Comparator<ByteString> ID = ByteString.unsignedLexicographicalComparator();
    static final Comparator<String> TEXT = Comparator.comparing(ByteString::copyFromUtf8, ID);

    private ByteOrder() {
    }
}
