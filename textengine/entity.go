package textengine

import (
	"github.com/google/uuid"
)

type EntityProperties map[string]string

type EntityRelationship struct {
	Type string
	OtherType string
	Other *Entity
	Properties EntityProperties
}

type EntityRelationshipKey struct {
	Id string
	Type string
}

type EntityRelationships map[EntityRelationshipKey]EntityRelationship

type EntityComponent struct {
	Initialize func(*Entity)
	Update func(*Entity)
}

type EntityComponents []EntityComponent

type Entity struct {
	Id   string
	Game *Game
	ActionTime Time
	Relationships EntityRelationships
	Properties EntityProperties
	Components EntityComponents
}

func (entity *Entity) AddRelationship(other *Entity, selftype string, othertype string) {
	key := EntityRelationshipKey{Id: other.Id, Type: selftype}
	otherkey := EntityRelationshipKey{Id: entity.Id, Type: othertype}

	entity.RemoveRelationship(other, selftype)

	entity.Relationships[key] = EntityRelationship{
		Type: selftype,
		OtherType: othertype,
		Other: other,
	}

	other.Relationships[otherkey] = EntityRelationship{
		Type: othertype,
		OtherType: selftype,
		Other: entity,
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

func (game *Game) NewEntity(properties EntityProperties) *Entity {
	entity := &Entity{
		Id:   uuid.New().String(),
		Game: game,
		Properties: properties,
		Relationships: EntityRelationships{},
		Components: EntityComponents{},
	}

	return entity
}
