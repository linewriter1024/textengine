package textengine

type LookDescriptor struct {
	entity EntityRef
	look_type string
	look_desc string
}

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
	_, err := entity.game.database.Exec("INSERT INTO entity_look(entityid, type, desc) VALUES (?, ?, ?)", entity.id, looktype, desc)
	return err
}

type LookDescriptorResult struct {
	look LookDescriptor
	err error
}

func (entity EntityRef) GetLooks() (chan LookDescriptorResult) {
	c := make(chan LookDescriptorResult)

	go func() {

		defer close(c)

		rows, err := entity.game.database.Query("SELECT l.entityid, l.type, l.desc FROM entity_look l WHERE l.entityid = ?", entity.id)

		if err != nil {
			c <- LookDescriptorResult{LookDescriptor{}, err}
			return
		}

		defer rows.Close()

		for rows.Next() {
			var look LookDescriptor
			var entity_id string

			if err := rows.Scan(&entity_id, &look.look_type, &look.look_desc); err != nil {
				c <- LookDescriptorResult{LookDescriptor{}, err}
				return
			}

			look.entity = entity.game.GetEntity(entity_id)

			c <- LookDescriptorResult{look, nil}
		}

		if err = rows.Err(); err != nil {
			c <- LookDescriptorResult{LookDescriptor{}, err}
		}
	}()

	return c
}
