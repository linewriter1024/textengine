package textengine

// A game. Includes the entire world and all actors.
type Game struct {
	commands Commands
	entities map[string]*Entity
	clients []*Client
	time     Time
	running bool
}

// Create a new game context.
func NewGame() *Game {
	game := &Game{
		commands: make(Commands),
		entities: make(map[string]*Entity),
		clients: make([]*Client, 0),
		running: true,
	}

	CommandUnknownRegister(game.commands)
	CommandEchoRegister(game.commands)

	CommandQuitRegister(game, game.commands)
	CommandWaitRegister(game, game.commands)

	return game
}

func (game *Game) Exit() {
	for _, client := range(game.clients) {
		client.Send(CommandOutput{"text": "Farewell."})
	}
	game.running = false
}

func (game *Game) RegisterClient(client *Client) {
	game.clients = append(game.clients, client)
	client.Send(CommandOutput{"text": "Welcome."})
}

func (game *Game) Loop() {
	for game.running {
		for _, client := range(game.clients) {
			command := client.Wait()
			game.FeedCommand(client, command)
		}
	}
}
