package textengine

func CommandEchoRegister(commands *Commands) {
	command := commands.Register("echo", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"output": "echo", "text": args["text"]})
	})

	command.Register("echo", `^echo[^\w]*(.*)$`, func(args []string) (CommandInput, error) {
		return CommandInput{"text": args[1]}, nil
	})
}
