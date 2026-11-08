# Changelog

## 1.0.0

Changes since `0.1.0-platform.61`.

### Breaking changes

- `PlayerProfile`, `PlayerSession`, `DeploymentHost`, `CacheStats`, `GroupStartQueue`, `GroupStartQueueEntry`, `GroupStartQueueItem` and `ProxyPresencePlayer` are now records.
  - Accessors changed from `getX()` to `x()`.
  - Setters, no-arg constructors and the `DeploymentHost` builder were removed.
- `RuntimeType`: removed `DOCKER`, `NODE` and `PYTHON`, added `BINARY`.
- `ScalingMode`: removed `PLAYERS` and `MANUAL`, added `SERVERS`.
- `DeploymentStrategy`: removed `RANDOM`, `PRIORITY` and `ROUND_ROBIN`.
- `ServerQuery`:
  - `getSortBy()` and `getSortOrder()` now return enums.
  - `getServerhostId()` and `getPersistentServerId()` became `getServerhostIds()` and `getPersistentServerIds()`.
  - `filterByServerhostId` and `filterByPersistentServerId` now take varargs, and repeated calls add values instead of replacing them.
- `GroupQuery`: removed `sortBy` and `sortOrder`.
- `ServerStoppedEvent.getCrashed()` was renamed to `isCrashed()`.
- `UpdateServerRequest`: removed `playerCount`.
- The Folia module was removed. Use the Paper jar, which now supports Folia.
- The new controller-routed connect methods can complete exceptionally, for example when the controller is unreachable.
- Plugins compiled against the previous version must be rebuilt.

### Deprecated

- `connect(serverName)`: use `connectToServerByName` or `connectDirect`. It still connects directly through the proxy.
- `connectToGroup(...)`: use `connectToGroupByName(...)`.
- `connectToPersistentServer(id)`: use `connectToPersistentServerById(id)`.
- `ServerQuery.sortBy(String)` and `sortOrder(String)`: use the `SortField` and `SortOrder` overloads.
- `GroupQuery.getTag()`: use `getTags()`.

### Added

- Blueprint API (`CloudApi.blueprint()`).
- Stats API (`CloudApi.stats()`) and `Group.getStats()`.
- Group API:
  - `stopAllServers`
  - rolling restarts: `startRollingRestart`, `getRollingRestartStatus` and `cancelRollingRestart`
  - `convertToPersistentServer`
  - `setGroupProperties`
- Persistent server API: `convertToGroup` and `setPersistentServerProperties`.
- Server API: `sendCommand` and `setServerProperties`.
- `executeWorkflow` for groups, servers and persistent servers.
- `connectTo(ConnectTarget)` returns the result and the chosen server. There are also by-name and by-id connect helpers.
- `ConnectResult.NO_SERVER_AVAILABLE` is returned by the new connect methods when no server can take the player. The deprecated connect methods still return `SERVER_NOT_FOUND` in that case.
- Persistent server events expose `getPersistentServer()`. The stop event also exposes `isCrashed()`, `getExitCode()` and `getReason()`.
- Queries:
  - paging with `offset`, plus `limit` on `ServerQuery`
  - new filters for active state, source type, blueprint and type
  - `copy()`
  - `GroupQuery.filterByTags(String...)`
- `PlayerApi.listPage`, `getProfilePage` and `getSessionPage` return a `Page` with the total count.
- `CreateGroupRequest` and `CreatePersistentServerRequest` take `plugins` (`PluginReference`) and `pluginCollectionIds`.
- Server events expose `getPersistentServerId()`, and `ServerStateChangedEvent` also exposes `getServerGroupId()`.
- Minestom platform.

### Fixed

- Unknown enum values from the controller map to `UNKNOWN_*` instead of throwing.
- Player session changes are reported in order.
- Proxy connects time out instead of hanging.
- Group scaling updates no longer apply create defaults.
- Groups created with default `SLOTS` scaling now start servers. Missing `availableSlots` defaults to the max players.
- The cache refreshes player counts on player login, disconnect and server switch, and refreshes persistent servers on their server's events.
- Updates called with an upper-case ID no longer leave stale cache entries.
- `connectDirect` reports an unreachable player after 10 seconds instead of 30.
