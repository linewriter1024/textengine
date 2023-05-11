package textengine

func systemPositionInitialize(system *System) error {
	return nil
}

func SystemPositionRegister(game *Game) {
	game.RegisterSystem("position", System{
		databaseInitialize: systemPositionInitialize,
	})
}
