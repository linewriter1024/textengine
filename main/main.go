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
					fmt.Printf("> (%s)\n", pretty(commandinput))
				}
				return commandinput, nil
			}
		},
		func(client *textengine.Client, output textengine.CommandOutput) {
			if apidebug {
				fmt.Printf("< (%s)\n", pretty(output))
			}

			if _, exists := output["text"]; exists {
				fmt.Printf("%s\n", output["text"])
			}
		},
	)

	game.RegisterClient(client)
	game.LoopWithClients()
}
