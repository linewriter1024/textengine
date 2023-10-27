package textengine

func (game *Game) NewPlaceEntity() (EntityRef, error) {
	var entity EntityRef
	var err error

	entity, err = game.NewEntity()

	if err != nil {
		return entity, err
	}

	err = entity.AddLook("basic", "a place")

	if err != nil {
		return entity, err
	}

	err = entity.SetPositionScale("area")

	if err != nil {
		return entity, err
	}

	return entity, nil
}
