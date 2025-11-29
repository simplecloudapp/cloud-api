package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerUpdatedEvent;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class ServerUpdatedEventImpl implements ServerUpdatedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerUpdatedEvent delegate;
    private Server server;

    ServerUpdatedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerUpdatedEvent delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNetworkId() {
        return delegate.getNetworkId();
    }

    @Override
    public String getServerId() {
        return delegate.getServerId();
    }

    @Override
    public String getServerGroupId() {
        return delegate.getServerGroupId();
    }

    @Override
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig()) {
                throw new IllegalStateException("Server data is not available in ServerUpdatedEvent");
            }
            ModelsServerSummary summary = new ModelsServerSummary();
            summary.setServerId(delegate.getServerId());
            summary.setServerGroupId(delegate.getServerGroupId());
            summary.setNetworkId(delegate.getNetworkId());

            if (delegate.hasConfig()) {
                var config = delegate.getConfig();
                summary.setMinMemory(config.getMinMemory());
                summary.setMaxMemory(config.getMaxMemory());
                summary.setMaxPlayers(config.getMaxPlayers());
                if (config.getPropertiesCount() > 0) {
                    Map<String, Object> props = new HashMap<>();
                    config.getPropertiesMap().forEach((k, v) -> props.put(k, v));
                    summary.setProperties(props);
                }
            }

            if (delegate.hasRuntimeInfo()) {
                var runtime = delegate.getRuntimeInfo();
                summary.setNumericalId(runtime.getNumericalId());
                summary.setIp(runtime.getIp());
                summary.setPort(runtime.getPort());
                if (!runtime.getServerhostId().isEmpty()) {
                    summary.setServerhostId(runtime.getServerhostId());
                }
            }

            if (delegate.hasGroupConfig() && delegate.getGroupConfig().hasBaseConfig()) {
                ModelsServerGroupInfo groupInfo = new ModelsServerGroupInfo();
                var groupConfig = delegate.getGroupConfig();
                var baseConfig = groupConfig.getBaseConfig();
                groupInfo.setId(delegate.getServerGroupId());
                groupInfo.setName(baseConfig.getName());
                groupInfo.setType(convertServerTypeToString(baseConfig.getType()));
                summary.setServerGroup(groupInfo);
            }

            server = new ServerImpl(summary);
        }
        return server;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }

    private String convertServerTypeToString(build.buf.gen.simplecloud.controller.v2.ServerType protoType) {
        if (protoType == null) {
            return null;
        }

        String name = protoType.name();
        if (name == null || name.isEmpty() || name.equals("UNRECOGNIZED")) {
            return null;
        }

        if (name.startsWith("SERVER_TYPE_")) {
            return name.substring("SERVER_TYPE_".length());
        }

        return name;
    }
}


