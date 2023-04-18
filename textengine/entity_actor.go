package textengine

func (game *Game) NewActor() *Entity {
	entity := game.NewEntity()
	return entity
}
