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
type PlayerQuery = sdk.PlayerQuery
type PlayerProfile = sdk.PlayerProfile
type PlayerSession = sdk.PlayerSession
type PlayerClientSettings = sdk.PlayerClientSettings
type PlayersClient = sdk.PlayersClient

type ServerSelectionMode = sdk.ServerSelectionMode

const (
	SelectRandom     = sdk.SelectRandom
	SelectMinPlayers = sdk.SelectMinPlayers
	SelectMaxPlayers = sdk.SelectMaxPlayers
	SelectFirst      = sdk.SelectFirst
)

type PlayerConnectResult = sdk.PlayerConnectResult

const (
	PlayerConnectSuccess          = sdk.PlayerConnectSuccess
	PlayerConnectAlreadyConnected = sdk.PlayerConnectAlreadyConnected
	PlayerConnectServerNotFound   = sdk.PlayerConnectServerNotFound
	PlayerConnectFailed           = sdk.PlayerConnectFailed
)

type Blueprint = sdk.Blueprint
type BlueprintsClient = sdk.BlueprintsClient

type Stats = sdk.Stats
type StatsClient = sdk.StatsClient

func DefaultOptions() Options { return sdk.DefaultOptions() }

func NewClient(options Options) (*Client, error) { return sdk.NewClient(options) }

func NewClientFromEnv() (*Client, error) { return sdk.NewClientFromEnv() }
