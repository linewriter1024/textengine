package main

import (
	"benleskey.com/packages/golang/textengine"
	"bufio"
	"fmt"
	"os"
)

func main() {
	game := textengine.NewGame()

	fmt.Printf("Connecting to %s %s\n", textengine.VersionFriendlyName, textengine.VersionVersion().String())

	client := game.NewClient(
		func(client *textengine.Client) textengine.CommandInput {
			scanner := bufio.NewScanner(os.Stdin)
			fmt.Print("> ")
			scanned := scanner.Scan()
			text := scanner.Text()
			if len(text) == 0 && !scanned {
				return textengine.CommandInput{"command": "quit"}
			} else {
				return client.TextToCommandInput(text)
			}
		},
		func(client *textengine.Client, output textengine.CommandOutput) {
			fmt.Printf("%s\n", output["text"])
		},
	)

	game.RegisterClient(client)
	game.Loop()
}
