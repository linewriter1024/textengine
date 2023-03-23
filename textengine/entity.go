package textengine

import (
	"github.com/google/uuid"
)

type Entity struct {
	id   string
	game *Game
}

func (game *Game) AddEntity(entity *Entity) {
	game.entities[entity.id] = entity
}

func (game *Game) NewEntity() *Entity {
	entity := &Entity{
		id:   uuid.New().String(),
		game: game,
	}

	return entity
}
