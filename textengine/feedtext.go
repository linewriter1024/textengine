package textengine

//import "regexp"

// Feed a line of text into a game.
// The text will be processed into a command, and then executed in the game.
// The resulting output of the command will be returned.
func (g *Game) FeedText(text string) string {
	command := make(map[string]string)

	return g.FeedCommand(command)["text"]
}
