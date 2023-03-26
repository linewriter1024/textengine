package textengine

func (game *Game) NewActor() *Entity {
	entity := game.NewEntity(EntityProperties{})
	return entity
}
