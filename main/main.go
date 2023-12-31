package main

import (
	"benleskey.com/packages/golang/textengine"
	"bufio"
	"flag"
	"fmt"
	"io"
	"os"
	"sort"
)

var apidebug = flag.Bool("apidebug", false, "print api debug information with each command")
var showlog = flag.Bool("showlog", false, "print game log to standard output")

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

	flag.Parse()

	var logWriter io.Writer

	if *showlog {
		logWriter = os.Stdout
	} else {
		logWriter = io.Discard
	}

	game, err := textengine.NewGame(logWriter)

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
				if *apidebug {
					fmt.Printf("> (%s)\n", pretty(commandinput))
				}
				return commandinput, nil
			}
		},
		func(client *textengine.Client, output textengine.CommandOutput, input textengine.CommandInput, gameError error) {
			if gameError != nil {
				fmt.Printf("< ! %v\n", gameError)
			}

			if *apidebug {
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
