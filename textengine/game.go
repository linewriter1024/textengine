package textengine

import (
	"database/sql"
	"fmt"
	_ "github.com/mattn/go-sqlite3"
)

// A game. Includes the entire world and all actors.
type Game struct {
	commands Commands
	clients  []*Client
	time     Time
	running  bool
	world EntityRef
	database *sql.DB
}

// Create a new game context.
func NewGame() (*Game, error) {
	game := &Game{
		commands: make(Commands),
		clients:  make([]*Client, 0),
		running:  true,
	}

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

	CommandUnknownRegister(game.commands)
	CommandEchoRegister(game.commands)

	CommandQuitRegister(game, game.commands)
	CommandWaitRegister(game, game.commands)

	return game, nil
}

func (game *Game) Exit() {
	for _, client := range game.clients {
		client.Quit()
	}
	game.running = false
}

func (game *Game) RegisterClient(client *Client) {
	game.clients = append(game.clients, client)
	client.Send(CommandOutput{
		"output":       "welcome",
		"friendlyname": VersionFriendlyName,
		"name":         VersionName,
		"version":      VersionVersion().String(),
		"text":         fmt.Sprintf("Welcome to %s %s", VersionFriendlyName, VersionVersion().String()),
	})
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
			game.Exit()
		}

		game.Process()
	}
}
