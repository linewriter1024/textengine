package textengine

type inputfunc func(*Client) CommandInput
type outputfunc func(*Client, CommandOutput)

type Client struct {
	game         *Game
	waitforinput inputfunc
	sendoutput   outputfunc
}

func (client *Client) Wait() CommandInput {
	return client.waitforinput(client)
}

func (client *Client) Send(output CommandOutput) {
	client.sendoutput(client, output)
}

func (game *Game) NewClient(waitforinput inputfunc, sendoutput outputfunc) *Client {
	return &Client{game: game, waitforinput: waitforinput, sendoutput: sendoutput}
}
