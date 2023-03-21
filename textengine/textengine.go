package textengine

// A game. Includes the entire world and all actors.
type Game struct {
}

// Create a new game context.
func NewGame() *Game {
	return new(Game)
}

// Feed a command into a game.
// Will return the output of the command.
// The "text" key in the returned output is the human readable output of the command.
func (g *Game) FeedCommand(command map[string]string) map[string]string {
	return map[string]string{
		"text": command["command"],
	}
}
