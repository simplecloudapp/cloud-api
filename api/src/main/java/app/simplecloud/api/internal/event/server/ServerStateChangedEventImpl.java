package app.simplecloud.api.internal.event.server;

import app.simplecloud.api.event.server.ServerStateChangedEvent;
import app.simplecloud.api.internal.ProtoConversionUtil;
import app.simplecloud.api.internal.persistentserver.PersistentServerImpl;
import app.simplecloud.api.internal.server.ServerImpl;
import app.simplecloud.api.server.Server;
import app.simplecloud.api.server.ServerState;
import app.simplecloud.api.web.models.ModelsPersistentServerInfo;
import app.simplecloud.api.web.models.ModelsServerGroupInfo;
import app.simplecloud.api.web.models.ModelsServerSummary;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class ServerStateChangedEventImpl implements ServerStateChangedEvent {
    private final build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent delegate;
    private Server server;

    ServerStateChangedEventImpl(build.buf.gen.simplecloud.controller.v2.ServerStateChangedEvent delegate) {
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
    public ServerState getOldState() {
        ServerState state = ProtoConversionUtil.convertServerState(delegate.getOldState());
        if (state == null) {
            throw new IllegalStateException("Old state is not available in ServerStateChangedEvent");
        }
        return state;
    }

    @Override
    public ServerState getNewState() {
        ServerState state = ProtoConversionUtil.convertServerState(delegate.getNewState());
        if (state == null) {
            throw new IllegalStateException("New state is not available in ServerStateChangedEvent");
        }
        return state;
    }

    @Override
    public Server getServer() {
        if (server == null) {
            if (!delegate.hasConfig() && !delegate.hasGroupConfig()) {
                throw new IllegalStateException("Server data is not available in ServerStateChangedEvent");
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
                summary.setState(ModelsServerSummary.StateEnum.valueOf(ProtoConversionUtil.convertServerStateToString(runtime.getState())));
                summary.setPlayerCount(-1); // TODO: implement with real player count by adding it to runtime info
                if (!runtime.getServerhostId().isEmpty()) {
                    summary.setServerhostId(runtime.getServerhostId());
                }
            }

            summary.setState(ModelsServerSummary.StateEnum.valueOf(ProtoConversionUtil.convertServerStateToString(delegate.getNewState())));

            ServerImpl serverImpl = new ServerImpl(summary);

            if (delegate.hasGroupConfig() && delegate.getGroupConfig().hasBaseConfig()) {
                ModelsServerGroupInfo groupInfo = new ModelsServerGroupInfo();
                var groupConfig = delegate.getGroupConfig();
                var baseConfig = groupConfig.getBaseConfig();
                groupInfo.setId(delegate.getServerGroupId());
                groupInfo.setName(baseConfig.getName());
                groupInfo.setType(ProtoConversionUtil.convertServerTypeToString(baseConfig.getType()));
                summary.setServerGroup(groupInfo);
            } else if (delegate.hasPersistentServerConfig() && delegate.getPersistentServerConfig().hasBaseConfig()) {
                ModelsPersistentServerInfo persistentServerInfo = new ModelsPersistentServerInfo();
                var psConfig = delegate.getPersistentServerConfig();
                var baseConfig = psConfig.getBaseConfig();
                persistentServerInfo.setId(delegate.getPersistentServerId());
                persistentServerInfo.setName(baseConfig.getName());
                persistentServerInfo.setType(ProtoConversionUtil.convertServerTypeToString(baseConfig.getType()));
                summary.setPersistentServerId(delegate.getPersistentServerId());
                serverImpl.setPersistentServer(new PersistentServerImpl(persistentServerInfo));
            }

            server = serverImpl;
        }
        return server;
    }

    @Override
    public String getTimestamp() {
        return Instant.ofEpochSecond(delegate.getTimestamp()).toString();
    }
}

