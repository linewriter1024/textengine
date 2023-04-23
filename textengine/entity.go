package textengine

import (
	"github.com/google/uuid"
)

type EntityRef struct {
	Id            string
	Game          *Game
}

func (entity EntityRef) AddRelationship(other EntityRef, selftype string, othertype string) {
}

func (entity EntityRef) RemoveRelationship(other EntityRef, selftype string) {
}

func (game *Game) NewEntity() EntityRef {
	entityRef := EntityRef{
		Id:            uuid.New().String(),
		Game:          game,
	}

	return entityRef
}
