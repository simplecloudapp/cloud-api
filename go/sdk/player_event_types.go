package simplecloud

import playerv2 "buf.build/gen/go/simplecloud/player/protocolbuffers/go"

type PlayerLoginEvent = playerv2.PlayerLoginEvent
type PlayerDisconnectEvent = playerv2.PlayerDisconnectEvent
type PlayerServerSwitchEvent = playerv2.PlayerServerSwitchEvent
type PlayerKickEvent = playerv2.PlayerKickEvent
type PlayerProfileChangedEvent = playerv2.PlayerProfileChangedEvent
type PlayerClientSettingsUpdatedEvent = playerv2.PlayerClientSettingsUpdatedEvent

type PlayerConnectResult = playerv2.ConnectResult

const (
	PlayerConnectSuccess          = playerv2.ConnectResult_CONNECT_RESULT_SUCCESS
	PlayerConnectAlreadyConnected = playerv2.ConnectResult_CONNECT_RESULT_ALREADY_CONNECTED
	PlayerConnectServerNotFound   = playerv2.ConnectResult_CONNECT_RESULT_SERVER_NOT_FOUND
	PlayerConnectFailed           = playerv2.ConnectResult_CONNECT_RESULT_FAILED
)
