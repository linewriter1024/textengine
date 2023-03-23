package textengine

import (
	"regexp"
	"strings"
)

type CommandInput map[string]string

type CommandOutput map[string]string

type CommandVariantFunction func([]string) (CommandInput, error)

type CommandVariant struct {
	command  *Command
	name     string
	regex    *regexp.Regexp
	function CommandVariantFunction
}

type CommandVariants map[string]*CommandVariant

type CommandFunction func(*Client, CommandInput)

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

func (variant *CommandVariant) Run(normalizedtext string) (bool, CommandInput, error) {
	if match := variant.regex.FindStringSubmatch(normalizedtext); len(match) > 0 {
		commandmap, err := variant.function(match)
		if err == nil {
			commandmap["command"] = variant.command.name
			return true, commandmap, nil
		} else {
			return false, nil, err
		}
	} else {
		return false, nil, nil
	}
}

func (g *Game) FeedCommand(client *Client, command CommandInput) {
	g.commands[command["command"]].function(client, command)
}

func (client *Client) TextToCommandInput(text string) CommandInput {
	normalized := strings.ToLower(text)

	for _, command := range client.game.commands {
		for _, variant := range command.variants {
			ran, commandmap, err := variant.Run(normalized)
			if ran {
				return commandmap
			} else if err != nil {
				return CommandInput{
					"command": "errorcommand",
					"error":   err.Error(),
				}
			}
		}
	}

	return CommandInput{
		"command": "unknowncommand",
		"text":    text,
	}
}
