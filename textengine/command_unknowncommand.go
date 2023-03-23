package textengine

func CommandUnknownRegister(commands Commands) {
	commands.Register("unknowncommand", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"text": "Unknown command: " + args["text"]})
	})

	commands.Register("errorcommand", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"text": "Error: " + args["error"]})
	})
}
