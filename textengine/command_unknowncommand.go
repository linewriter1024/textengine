package textengine

func CommandUnknownRegister(commands Commands) {
	commands.Register("unknowncommand", func(args CommandInput) CommandOutput {
		return CommandOutput{"text": "Unknown command: " + args["text"]}
	})
}
