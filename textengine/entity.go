package textengine

import (
	"github.com/google/uuid"
)

type Entity struct {
	Id   string
	Game *Game
}

func (game *Game) AddEntity(entity *Entity) {
	game.entities[entity.Id] = entity
}

func (game *Game) NewEntity() *Entity {
	entity := &Entity{
		Id:   uuid.New().String(),
		Game: game,
	}

	return entity
}
