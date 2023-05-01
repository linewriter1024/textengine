package textengine

type System struct {
	Game               *Game
	SystemId           string
	DatabaseInitialize func(*System) error
}

type Systems map[string]*System

func (game *Game) RegisterSystem(systemId string, system System) *System {
	system.Game = game
	system.SystemId = systemId

	game.systems[systemId] = &system

	return &system
}
