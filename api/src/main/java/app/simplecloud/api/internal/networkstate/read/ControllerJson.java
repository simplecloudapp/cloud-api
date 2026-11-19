package app.simplecloud.api.internal.networkstate.read;

import app.simplecloud.api.web.JSON;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Reads the JSON the controller rendered with the HTTP client's Gson, so that an object built from a copy
 * holds the same values as an object read over HTTP.
 */
final class ControllerJson {

    private static final Type PROPERTIES = new TypeToken<Map<String, Object>>() {
    }.getType();

    private ControllerJson() {
    }

    /**
     * Reads JSON into a generated model, as the HTTP client reads a response body.
     */
    static <T> T toModel(ByteString json, Class<T> model) {
        return JSON.deserialize(json.toStringUtf8(), model);
    }

    /**
     * Reads a properties object, or null where the controller wrote null.
     */
    static @Nullable Map<String, Object> toProperties(ByteString json) {
        return JSON.deserialize(json.toStringUtf8(), PROPERTIES);
    }
}
