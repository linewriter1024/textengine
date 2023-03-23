package textengine

type inputfunc func(*Client) (CommandInput, error)
type outputfunc func(*Client, CommandOutput)

type Client struct {
	game         *Game
	alive        bool
	waitforinput inputfunc
	sendoutput   outputfunc
}

func (client *Client) Wait() (CommandInput, error) {
	return client.waitforinput(client)
}

func (client *Client) Send(output CommandOutput) {
	client.sendoutput(client, output)
}

func (client *Client) Quit() {
	client.alive = false
}

func (game *Game) NewClient(waitforinput inputfunc, sendoutput outputfunc) *Client {
	return &Client{game: game, alive: true, waitforinput: waitforinput, sendoutput: sendoutput}
}
