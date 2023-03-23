package main

import (
	"benleskey.com/packages/golang/textengine"
	"bufio"
	"fmt"
	"os"
)

func main() {
	const apidebug = true

	game := textengine.NewGame()

	client := game.NewClient(
		func(client *textengine.Client) (textengine.CommandInput, error) {
			scanner := bufio.NewScanner(os.Stdin)
			fmt.Print("> ")
			scanned := scanner.Scan()
			text := scanner.Text()
			if len(text) == 0 && !scanned {
				return textengine.CommandInput{"command": "quit"}, nil
			} else {
				commandinput := client.TextToCommandInput(text)
				if apidebug {
					fmt.Printf("> (%s)\n", commandinput);
				}
				return commandinput, nil
			}
		},
		func(client *textengine.Client, output textengine.CommandOutput) {
			if apidebug {
				fmt.Printf("< (%s)\n", output)
			}

			fmt.Printf("%s\n", output["text"]);
		},
	)

	game.RegisterClient(client)
	game.LoopWithClients()
}
