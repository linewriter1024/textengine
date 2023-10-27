package main

import (
	"benleskey.com/packages/golang/textengine"
	"bufio"
	"fmt"
	"os"
	"sort"
)

func pretty(m map[string]string) string {
	r := ""

	keys := make([]string, 0)
	for k := range m {
		keys = append(keys, k)
	}

	sort.Strings(keys)

	for i, k := range keys {
		if i > 0 {
			r += ", "
		}
		r += fmt.Sprintf("%s: %q", k, m[k])
	}

	return r
}

func main() {
	const apidebug = true

	game, err := textengine.NewGame()

	if err != nil {
		fmt.Printf("Could not open game: %s\n", err)
		os.Exit(1)
	}

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
					fmt.Printf("> (%s)\n", pretty(commandinput))
				}
				return commandinput, nil
			}
		},
		func(client *textengine.Client, output textengine.CommandOutput, input textengine.CommandInput, gameError error) {
			if gameError != nil {
				fmt.Printf("< ! %v\n", gameError)
			}

			if apidebug {
				fmt.Printf("< (%s)\n", pretty(output))
			}

			if _, exists := output["text"]; exists {
				fmt.Printf("%s\n", output["text"])
			}
		},
	)

	{
		err := game.RegisterClient(client)

		if err != nil {
			fmt.Printf("Could not register client: %s\n", err)
			os.Exit(1)
		}
	}

	game.LoopWithClients()
}
