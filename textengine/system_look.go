package textengine

func systemLookInitialize(system *System) error {
	v, err := system.GetSchemaVersionNumber()
	if err != nil {
		return err
	}

	if v == 0 {
		_, err := system.game.database.Exec("CREATE TABLE IF NOT EXISTS entity_look(entityid TEXT, type TEXT, desc TEXT, FOREIGN KEY (entityid) REFERENCES entity(id))")
		if err != nil {
			return err
		}
		system.SetSchemaVersionNumber(1)
	}

	return nil
}

func SystemLookRegister(game *Game) {
	game.RegisterSystem("look", System{
		databaseInitialize: systemLookInitialize,
	})
}

func (entity *EntityRef) AddLook(looktype string, desc string) error {
	_, err := entity.game.database.Exec("INSERT INTO entity_look(entityid, type, desc) VALUES (?, ?, ?)", entity.id, looktype, desc);
	return err
}
