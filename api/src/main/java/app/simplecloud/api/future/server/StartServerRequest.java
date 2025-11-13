package app.simplecloud.api.future.server;


import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface StartServerRequest {
    String getServerGroupId();
    String getServerGroupName();
    @Nullable String getServerhostId();
    Map<String, Object> getProperties();
}