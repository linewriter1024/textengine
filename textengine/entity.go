package textengine

import (
	"github.com/google/uuid"
)

type EntityRelationship struct {
	Type      string
	OtherType string
	Other     *Entity
}

type EntityRelationshipKey struct {
	Id   string
	Type string
}

type EntityRelationships map[EntityRelationshipKey]EntityRelationship

type Entity struct {
	Id            string
	Game          *Game
	Relationships EntityRelationships
}

func (entity *Entity) AddRelationship(other *Entity, selftype string, othertype string) {
	key := EntityRelationshipKey{Id: other.Id, Type: selftype}
	otherkey := EntityRelationshipKey{Id: entity.Id, Type: othertype}

	entity.RemoveRelationship(other, selftype)

	entity.Relationships[key] = EntityRelationship{
		Type:      selftype,
		OtherType: othertype,
		Other:     other,
	}

	other.Relationships[otherkey] = EntityRelationship{
		Type:      othertype,
		OtherType: selftype,
		Other:     entity,
	}
}

func (entity *Entity) RemoveRelationship(other *Entity, selftype string) {
	key := EntityRelationshipKey{Id: other.Id, Type: selftype}

	if relationship, ok := entity.Relationships[key]; ok {
		otherkey := EntityRelationshipKey{Id: entity.Id, Type: relationship.OtherType}

		delete(entity.Relationships, key)
		delete(other.Relationships, otherkey)
	}
}

func (game *Game) AddEntity(entity *Entity) {
	game.entities[entity.Id] = entity
}

func (game *Game) NewEntity() *Entity {
	entity := &Entity{
		Id:            uuid.New().String(),
		Game:          game,
		Relationships: EntityRelationships{},
	}

	return entity
}
