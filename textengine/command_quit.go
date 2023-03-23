package textengine

func CommandQuitRegister(game *Game, commands Commands) {
	command := commands.Register("quit", func(client *Client, args CommandInput) {
		game.Exit()
	})

	command.Register("echo", `^quit([^\w]+|$)`, func(args []string) (CommandInput, error) {
		return CommandInput{}, nil
	})
}
