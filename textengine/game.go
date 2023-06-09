package textengine

import "fmt"

// A game. Includes the entire world and all actors.
type Game struct {
	commands Commands
	entities map[string]*Entity
	clients  []*Client
	time     Time
	running  bool
	world *Entity
}

// Create a new game context.
func NewGame() *Game {
	game := &Game{
		commands: make(Commands),
		entities: make(map[string]*Entity),
		clients:  make([]*Client, 0),
		running:  true,
	}

	CommandUnknownRegister(game.commands)
	CommandEchoRegister(game.commands)

	CommandQuitRegister(game, game.commands)
	CommandWaitRegister(game, game.commands)

	game.world = game.NewPlace()

	return game
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

	actor := game.NewActor()
	game.AddEntity(actor)

	actor.AddRelationship(game.world, "in", "contains")

	client.SetEntity(actor)
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
