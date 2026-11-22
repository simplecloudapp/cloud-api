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

func DefaultOptions() Options { return sdk.DefaultOptions() }

func NewClient(options Options) (*Client, error) { return sdk.NewClient(options) }

func NewClientFromEnv() (*Client, error) { return sdk.NewClientFromEnv() }
