package textengine

func CommandWaitRegister(game *Game, commands Commands) {
	command := commands.Register("wait", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"text": args["time"]})
	})

	command.Register("wait", `^wait[^\w]*(.*)$`, func(args []string) (CommandInput, error) {
		time, err := NewTime(args[1])
		if err == nil {
			return CommandInput{"time": time.String()}, nil
		} else {
			return CommandInput{}, err
		}
	})
}
