package textengine

type inputfunc func(*Client) (CommandInput, error)
type outputfunc func(*Client, CommandOutput, CommandInput, error)

type Client struct {
	game         *Game
	entity       EntityRef
	alive        bool
	waitforinput inputfunc
	sendoutput   outputfunc
}

func (client *Client) Wait() (CommandInput, error) {
	return client.waitforinput(client)
}

func (client *Client) Send(output CommandOutput) {
	client.sendoutput(client, output, nil, nil)
}

func (client *Client) SendAll(output CommandOutput, input CommandInput, err error) {
	client.sendoutput(client, output, input, err)
}

func (client *Client) Quit() {
	if client.alive {
		client.alive = false
		client.Send(CommandOutput{
			"output": "clientquit",
			"text":   "Goodbye.",
		})
	}
}

func (client *Client) SetEntity(entity EntityRef) {
	client.entity = entity
	client.Send(CommandOutput{
		"output": "controllingentity",
		"entity": client.entity.id,
	})
}

func (game *Game) NewClient(waitforinput inputfunc, sendoutput outputfunc) *Client {
	return &Client{
		game:         game,
		alive:        true,
		waitforinput: waitforinput,
		sendoutput:   sendoutput,
	}
}
