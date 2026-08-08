// Package simplecloud preserves the module-root API while the implementation
// lives in the sdk subpackage.
package simplecloud

import sdk "github.com/simplecloudapp/cloud-api/go/sdk"

const (
	DefaultControllerURL = sdk.DefaultControllerURL
	DefaultNATSURL       = sdk.DefaultNATSURL
)

var ErrNotFound = sdk.ErrNotFound

type Options = sdk.Options
type Client = sdk.Client

type Group = sdk.Group
type GroupQuery = sdk.GroupQuery
type GroupsClient = sdk.GroupsClient

type Server = sdk.Server
type ServerQuery = sdk.ServerQuery
type ServersClient = sdk.ServersClient

type PersistentServer = sdk.PersistentServer
type PersistentServerQuery = sdk.PersistentServerQuery
type PersistentServersClient = sdk.PersistentServersClient

type Player = sdk.Player
type PlayersClient = sdk.PlayersClient

type EventErrorHandler = sdk.EventErrorHandler
type EventsClient = sdk.EventsClient
type ServerGroupCreatedEvent = sdk.ServerGroupCreatedEvent
type ServerGroupUpdatedEvent = sdk.ServerGroupUpdatedEvent
type ServerGroupDeletedEvent = sdk.ServerGroupDeletedEvent
type ServerStartedEvent = sdk.ServerStartedEvent
type ServerStoppedEvent = sdk.ServerStoppedEvent
type ServerStateChangedEvent = sdk.ServerStateChangedEvent
type ServerDeletedEvent = sdk.ServerDeletedEvent
type ServerUpdatedEvent = sdk.ServerUpdatedEvent
type PersistentServerCreatedEvent = sdk.PersistentServerCreatedEvent
type PersistentServerStartedEvent = sdk.PersistentServerStartedEvent
type PersistentServerStoppedEvent = sdk.PersistentServerStoppedEvent
type PersistentServerUpdatedEvent = sdk.PersistentServerUpdatedEvent
type PersistentServerDeletedEvent = sdk.PersistentServerDeletedEvent
type BlueprintCreatedEvent = sdk.BlueprintCreatedEvent
type BlueprintUpdatedEvent = sdk.BlueprintUpdatedEvent
type BlueprintDeletedEvent = sdk.BlueprintDeletedEvent

type ModelsClearServerGroupStartQueueResponse = sdk.ModelsClearServerGroupStartQueueResponse
type ModelsConnectPlayerRequest = sdk.ModelsConnectPlayerRequest
type ModelsConnectPlayerResponse = sdk.ModelsConnectPlayerResponse
type ModelsCreatePersistentServerRequest = sdk.ModelsCreatePersistentServerRequest
type ModelsCreateServerGroupRequest = sdk.ModelsCreateServerGroupRequest
type ModelsDeletePersistentServerResponse = sdk.ModelsDeletePersistentServerResponse
type ModelsDeletePlayerPropertiesRequest = sdk.ModelsDeletePlayerPropertiesRequest
type ModelsDeletePropertiesRequest = sdk.ModelsDeletePropertiesRequest
type ModelsDeleteServerGroupResponse = sdk.ModelsDeleteServerGroupResponse
type ModelsKickPlayerRequest = sdk.ModelsKickPlayerRequest
type ModelsKickPlayerResponse = sdk.ModelsKickPlayerResponse
type ModelsListServerGroupStartQueueResponse = sdk.ModelsListServerGroupStartQueueResponse
type ModelsPatchPersistentServerRequest = sdk.ModelsPatchPersistentServerRequest
type ModelsPatchPlayerRequest = sdk.ModelsPatchPlayerRequest
type ModelsPatchPropertiesRequest = sdk.ModelsPatchPropertiesRequest
type ModelsPatchServerGroupRequest = sdk.ModelsPatchServerGroupRequest
type ModelsPatchServerRequest = sdk.ModelsPatchServerRequest
type ModelsPersistentServerSummary = sdk.ModelsPersistentServerSummary
type ModelsPlayerResponse = sdk.ModelsPlayerResponse
type ModelsQueueServerGroupStartRequest = sdk.ModelsQueueServerGroupStartRequest
type ModelsQueueServerGroupStartResponse = sdk.ModelsQueueServerGroupStartResponse
type ModelsServerGroupSummary = sdk.ModelsServerGroupSummary
type ModelsServerSummary = sdk.ModelsServerSummary
type ModelsStopServerResponse = sdk.ModelsStopServerResponse
type ModelsUpdatePlayerPropertiesRequest = sdk.ModelsUpdatePlayerPropertiesRequest

func DefaultOptions() Options { return sdk.DefaultOptions() }

func NewClient(options Options) (*Client, error) { return sdk.NewClient(options) }

func NewClientFromEnv() (*Client, error) { return sdk.NewClientFromEnv() }
