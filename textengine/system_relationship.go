package textengine

func databaseInitialize(system *System) error {
	v, err := system.GetSchemaVersionNumber()
	if err != nil {
		return err
	}

	if v == 0 {
		_, err := system.Game.database.Exec("CREATE TABLE IF NOT EXISTS entity_relationship(entityfromid TEXT, entitytoid TEXT, relationshipverbto TEXT, relationshipverb TEXT, PRIMARY KEY (entityfromid, entitytoid, relationshipverb))");
		if err != nil {
			return err
		}
		system.SetSchemaVersionNumber(1)
	}

	return nil
}

func SystemRelationshipRegister(game *Game) {
	game.RegisterSystem("relationship", System{
		DatabaseInitialize: databaseInitialize,
	})
}

func (entity EntityRef) AddRelationship(other EntityRef, verb string) error {
	_, err := entity.Game.database.Exec("INSERT OR REPLACE INTO entity_relationship(entityfromid, entitytoid, relationshipverb) VALUES (?, ?, ?)", entity.Id, other.Id, verb);
	return err

}

func (entity EntityRef) RemoveRelationship(other EntityRef, verb string) error {
	_, err := entity.Game.database.Exec("DELETE FROM entity_relationship WHERE entityfromid = ? AND entitytoid = ? AND relationshipverb = ?", entity.Id, other.Id, verb);
	return err
}
