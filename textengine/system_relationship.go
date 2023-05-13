package textengine

import (
	"github.com/google/uuid"
)

type Relationship struct {
	id       string
	game     *Game
	provider EntityRef
	receiver EntityRef
	verb     string
}

func systemRelationshipInitialize(system *System) error {
	v, err := system.GetSchemaVersionNumber()
	if err != nil {
		return err
	}

	if v == 0 {
		_, err := system.game.database.Exec("CREATE TABLE IF NOT EXISTS entity_relationship(relationshipid TEXT PRIMARY KEY, providerid TEXT, recieverid TEXT, relationshipverb TEXT, alive INTEGER)")
		if err != nil {
			return err
		}
		system.SetSchemaVersionNumber(1)
	}

	return nil
}

func SystemRelationshipRegister(game *Game) {
	game.RegisterSystem("relationship", System{
		databaseInitialize: systemRelationshipInitialize,
	})
}

func (entity EntityRef) AddRelationship(verb string, other EntityRef) (Relationship, error) {
	id := uuid.New().String()
	_, err := entity.game.database.Exec("INSERT INTO entity_relationship(relationshipid, providerid, recieverid, relationshipverb, alive) VALUES (?, ?, ?, ?, 1)", id, entity.id, other.id, verb)
	return Relationship{
		id:       id,
		game:     entity.game,
		provider: entity,
		receiver: other,
		verb:     verb,
	}, err
}

func (relationship Relationship) Remove() error {
	_, err := relationship.game.database.Exec("UPDATE entity_relationship WHERE relationshipid = ? SET alive = 0", relationship.id)
	return err
}
