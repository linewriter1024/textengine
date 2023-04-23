package textengine

type System struct {
	Game *Game
	SystemId string
	DatabaseInitialize func (*System)
}
