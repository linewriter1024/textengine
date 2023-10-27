package textengine

import (
	"fmt"
	"strings"
)

func CommandLookRegister(game *Game, commands *Commands) {
	command := commands.Register("look", func(client *Client, args CommandInput) {
		looks := client.entity.GetLooks()

		var looksText []string

		var output = make(CommandOutput)

		for lookResult := range looks {
			if lookResult.err != nil {
				client.SendAll(CommandOutput{}, args, lookResult.err)
				return
			}
			look := lookResult.look
			looksText = append(looksText, fmt.Sprintf("%s", look.look_desc))
			output[look.entity.id] = fmt.Sprintf("%s %q %q", output[look.entity.id], look.look_type, look.look_desc)
		}

		output["text"] = strings.Join(looksText, "\n")

		client.SendAll(output, args, nil)
	})

	command.Register("look", `^look$`, func(args []string) (CommandInput, error) {
		return CommandInput{}, nil
	})
}
