package textengine

type System struct {
	game               *Game
	id           string
	databaseInitialize func(*System) error
}

type Systems map[string]*System

func (game *Game) RegisterSystem(systemId string, system System) *System {
	system.game = game
	system.id = systemId

	game.systems[systemId] = &system

	return &system
}

func (game *Game) InitializeSystems() error {
	for _, system := range game.systems {
		var previousVersion int
		var nextVersion int
		var err error

		previousVersion, err = system.GetSchemaVersionNumber()
		if err != nil {
			return err
		}

		err = system.databaseInitialize(system)
		if err != nil {
			return err
		}

		nextVersion, err = system.GetSchemaVersionNumber()
		if err != nil {
			return err
		}

		if previousVersion == nextVersion {
			game.systemLog.Printf("System %q version %d", system.id, nextVersion)
		} else if previousVersion == 0 {
			game.systemLog.Printf("System %q initialized version %d", system.id, nextVersion)
		} else {
			game.systemLog.Printf("System %q version upgraded %d -> %d", system.id, previousVersion, nextVersion)
		}
	}

	return nil
}
