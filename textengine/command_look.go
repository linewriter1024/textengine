package textengine

func CommandLookRegister(game *Game, commands *Commands) {
	command := commands.Register("look", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"text": client.entity.id})
	})

	command.Register("look", `^look$`, func(args []string) (CommandInput, error) {
		return CommandInput{}, nil
	})
}
