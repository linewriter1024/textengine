package textengine

import (
	"github.com/google/uuid"
)

func systemEntityInitialize(system *System) error {
	v, err := system.GetSchemaVersionNumber()
	if err != nil {
		return err
	}

	if v == 0 {
		_, err := system.game.database.Exec("CREATE TABLE IF NOT EXISTS entity(id TEXT PRIMARY KEY)")
		if err != nil {
			return err
		}
		system.SetSchemaVersionNumber(1)
	}

	return nil
}

func SystemEntityRegister(game *Game) {
	game.RegisterSystem("entity", System{
		databaseInitialize: systemEntityInitialize,
	})
}

type EntityRef struct {
	id   string
	game *Game
}

func (game *Game) NewEntity() (EntityRef, error) {
	entityRef := EntityRef{
		id:   uuid.New().String(),
		game: game,
	}

	_, err := game.database.Exec("INSERT INTO entity(id) VALUES (?)", entityRef.id)

	return entityRef, err
}
