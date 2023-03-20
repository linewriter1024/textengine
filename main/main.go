package main

import (
	"benleskey.com/packages/golang/mud1024"
	"bufio"
	"fmt"
	"os"
)

func main() {
	game := mud1024.Game{}

	fmt.Printf("Welcome to %s %s\n", mud1024.VersionName, mud1024.VersionVersion().String())

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
