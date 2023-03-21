package main

import (
	"benleskey.com/packages/golang/textengine"
	"bufio"
	"fmt"
	"os"
)

func main() {
	game := textengine.NewGame()

	fmt.Printf("Welcome to %s %s\n", textengine.VersionName, textengine.VersionVersion().String())

	scanner := bufio.NewScanner(os.Stdin)
	for {
		fmt.Print("> ")
		scanner.Scan()
		text := scanner.Text()
		if len(text) == 0 {
			break
		} else {
			fmt.Printf("%s\n", game.FeedText(text))
		}
	}
}
