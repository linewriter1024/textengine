package textengine

func systemPositionInitialize(system *System) error {
	v, err := system.GetSchemaVersionNumber()
	if err != nil {
		return err
	}

	if v == 0 {
		_, err := system.game.database.Exec("CREATE TABLE IF NOT EXISTS entity_position_scale(entityid TEXT PRIMARY KEY, scale TEXT)")
		if err != nil {
			return err
		}
		system.SetSchemaVersionNumber(1)
	}

	return nil
}

func SystemPositionRegister(game *Game) {
	game.RegisterSystem("position", System{
		databaseInitialize: systemPositionInitialize,
	})
}

func (entity *EntityRef) SetPositionScale(scale string) error {
	_, err := entity.game.database.Exec("INSERT OR REPLACE INTO entity_position_scale(entityid, scale) VALUES (?, ?)", entity.id, scale)
	return err
}
