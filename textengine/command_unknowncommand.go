package textengine

func CommandUnknownRegister(commands *Commands) {
	commands.Register("unknowncommand", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"output": "unknowncommand", "command": args["text"], "text": "Unknown command: " + args["text"]})
	})

	commands.Register("errorcommand", func(client *Client, args CommandInput) {
		client.Send(CommandOutput{"output": "errorcommand", "error": args["error"], "text": "Error: " + args["error"]})
	})
}
