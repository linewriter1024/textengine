package textengine

func (game *Game) CreateSchemaTableIfNeeded() error {
	_, err := game.database.Exec("CREATE TABLE IF NOT EXISTS systemschema(systemid TEXT PRIMARY KEY, versionnumber INTEGER)")
	return err
}

func (game *Game) GetSchemaVersionNumber(systemid string) (int, error) {
	versionnumber := 0

	rows, err := game.database.Query("SELECT versionnumber FROM systemschema WHERE systemid = ?", systemid)

	if err != nil {
		return versionnumber, err
	}

	defer rows.Close()

	for rows.Next() {
		err = rows.Scan(&versionnumber)
	}

	return versionnumber, err
}

func (system *System) GetSchemaVersionNumber() (int, error) {
	return system.Game.GetSchemaVersionNumber(system.SystemId)
}

func (game *Game) SetSchemaVersionNumber(systemid string, versionnumber int) error {
	_, err := game.database.Exec("INSERT OR REPLACE INTO systemschema(systemid,versionnumber) VALUES(?, ?)", systemid, versionnumber)
	return err
}

func (system *System) SetSchemaVersionNumber(versionnumber int) error {
	return system.Game.SetSchemaVersionNumber(system.SystemId, versionnumber)
}
