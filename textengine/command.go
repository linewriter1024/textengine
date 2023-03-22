package textengine

import (
	"regexp"
	"strings"
)

type CommandInput map[string]string

type CommandOutput map[string]string

type CommandVariantFunction func([]string) CommandInput

type CommandVariant struct {
	command  *Command
	name     string
	regex    *regexp.Regexp
	function CommandVariantFunction
}

type CommandVariants map[string]*CommandVariant

type CommandFunction func(CommandInput) CommandOutput

type Command struct {
	name     string
	variants CommandVariants
	function CommandFunction
}

type Commands map[string]*Command

func (commands Commands) Register(name string, function CommandFunction) *Command {
	command := &Command{name: name, variants: make(CommandVariants), function: function}
	commands[name] = command
	return command
}

func (command *Command) Register(name string, regex string, function CommandVariantFunction) *CommandVariant {
	variant := &CommandVariant{command: command, name: name, regex: regexp.MustCompile(regex), function: function}
	command.variants[name] = variant
	return variant
}

func (variant *CommandVariant) Run(normalizedtext string) (bool, CommandInput) {
	if match := variant.regex.FindStringSubmatch(normalizedtext); len(match) > 0 {
		commandmap := variant.function(match)
		commandmap["command"] = variant.command.name
		return true, commandmap
	} else {
		return false, nil
	}
}

// Feed a command into a game.
// Will return the output of the command.
// The "text" key in the returned output is the human readable output of the command.
func (g *Game) FeedCommand(command CommandInput) CommandOutput {
	return g.commands[command["command"]].function(command)
}

// Feed a line of text into a game.
// The text will be processed into a command, and then executed in the game.
// The resulting output of the command will be returned.
func (g *Game) FeedText(text string) string {
	normalized := strings.ToLower(text)

	for _, command := range g.commands {
		for _, variant := range command.variants {
			ran, commandmap := variant.Run(normalized)
			if ran {
				return g.FeedCommand(commandmap)["text"]
			}
		}
	}

	return g.FeedCommand(CommandInput{
		"command": "unknowncommand",
		"text":    text,
	})["text"]
}
