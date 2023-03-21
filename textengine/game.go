package textengine

// A game. Includes the entire world and all actors.
type Game struct {
	commands Commands
}

// Create a new game context.
func NewGame() *Game {
	game := new(Game)
	game.commands = make(Commands)

	CommandUnknownRegister(game.commands)
	CommandEchoRegister(game.commands)

	return game
}
