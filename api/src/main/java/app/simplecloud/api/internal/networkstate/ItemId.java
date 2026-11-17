package app.simplecloud.api.internal.networkstate;

import com.google.protobuf.ByteString;

/**
 * One item of a network copy.
 *
 * @param id the item's 16-byte UUID, as it travels on the wire
 */
public record ItemId(ItemKind kind, ByteString id) {
}
