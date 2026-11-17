package app.simplecloud.api.internal.networkstate.read;

import com.google.protobuf.ByteString;

import java.util.Map;

/**
 * Follows a reference between items of a copy. A view checks after every batch and snapshot that each one
 * resolves, so a missing item here is a bug in the view, not a state of the network.
 */
final class CopyReferences {

    private CopyReferences() {
    }

    /**
     * @throws IllegalStateException if the copy has no such item
     */
    static <T> T resolve(Map<ByteString, T> items, ByteString id, String kind) {
        T item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("The copy has no " + kind + " " + ControllerJson.uuid(id));
        }
        return item;
    }
}
