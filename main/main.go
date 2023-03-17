package main

import (
    "fmt"
    "benleskey.com/packages/golang/mud1024"
)

func main() {
    // Get a greeting message and print it.
    message := mud1024.Hello("Gladys")
    fmt.Println(message)
}
