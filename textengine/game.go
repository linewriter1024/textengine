package textengine

import (
	"database/sql"
	"fmt"
	_ "github.com/mattn/go-sqlite3"
	"io"
	"log"
)

// A game. Includes the entire world and all actors.
type Game struct {
	commands  *Commands
	systems   Systems
	clients   []*Client
	time      Time
	running   bool
	world     EntityRef
	database  *sql.DB
	systemLog *log.Logger
}

// Create a new game context.
func NewGame(logWriter io.Writer) (*Game, error) {
	game := &Game{
		systems:   make(Systems),
		clients:   make([]*Client, 0),
		running:   true,
		systemLog: log.New(logWriter, "", log.LstdFlags),
	}

	game.commands = &Commands{game: game, commands: make(map[string]*Command)}

	game.systemLog.Printf("%q %s %s %s", VersionFriendlyName, VersionName, VersionVersion().String(), VersionURL)

	game.systemLog.Printf("Creating game context...")

	{
		db, err := sql.Open("sqlite3", ":memory:")
		if err != nil {
			return nil, err
		}

		game.database = db
	}

	{
		err := game.CreateSchemaTableIfNeeded()
		if err != nil {
			return nil, err
		}
	}

	SystemEntityRegister(game)
	SystemRelationshipRegister(game)
	SystemLookRegister(game)
	SystemPositionRegister(game)

	{
		err := game.InitializeSystems()
		if err != nil {
			return nil, err
		}
	}

	CommandUnknownRegister(game.commands)
	CommandEchoRegister(game.commands)

	CommandQuitRegister(game, game.commands)
	CommandWaitRegister(game, game.commands)

	CommandLookRegister(game, game.commands)

	return game, nil
}

func (game *Game) Exit() {
	game.systemLog.Printf("Exiting...")
	for _, client := range game.clients {
		client.Quit()
	}
	game.running = false
}

func (game *Game) RegisterClient(client *Client) error {
	game.systemLog.Printf("Client connected...")
	game.clients = append(game.clients, client)
	client.Send(CommandOutput{
		"output":       "welcome",
		"friendlyname": VersionFriendlyName,
		"name":         VersionName,
		"version":      VersionVersion().String(),
		"url":          VersionURL,
		"text":         fmt.Sprintf("Welcome to %s %s <%s>", VersionFriendlyName, VersionVersion().String(), VersionURL),
	})

	var err error

	var place EntityRef
	place, err = game.NewPlaceEntity()

	if err != nil {
		return err
	}

	var entity EntityRef
	entity, err = game.NewActorEntity()

	if err != nil {
		return err
	}

	place.AddRelationship("contains", entity)

	client.SetEntity(entity)

	return nil
}

func (game *Game) Process() {
	for _, client := range game.clients {
		if client.alive {
			command, err := client.Wait()
			if err == nil {
				game.FeedCommand(client, command)
			}
		}
	}
}

func (game *Game) LoopWithClients() {
	for game.running {
		livingclients := 0
		for _, client := range game.clients {
			if client.alive {
				livingclients += 1
			}
		}

		if livingclients == 0 {
			game.systemLog.Printf("No clients left alive")
			game.Exit()
		}

		game.Process()
	}
}
