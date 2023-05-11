package textengine

func (game *Game) NewActorEntity() (EntityRef, error) {
	var entity EntityRef
	var err error

	entity, err = game.NewEntity()

	if err != nil {
		return entity, err
	}

	err = entity.AddLook("basic", "actor")

	if err != nil {
		return entity, err
	}

	return entity, nil
}
