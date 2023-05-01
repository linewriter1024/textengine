package textengine

import (
	"github.com/google/uuid"
)

type EntityRef struct {
	Id   string
	Game *Game
}

func (game *Game) NewEntity() EntityRef {
	entityRef := EntityRef{
		Id:   uuid.New().String(),
		Game: game,
	}

	return entityRef
}
