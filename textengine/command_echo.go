package textengine

func CommandEchoRegister(commands Commands) {
	command := commands.Register("echo", func(args CommandInput) CommandOutput {
		return CommandOutput{"text": args["text"]}
	})

	command.Register("echo", `^echo[^\w]*(.*)$`, func(args []string) CommandInput {
		return CommandInput{"text": args[1]}
	})
}
