package textengine

func (game *Game) CreateSchemaTableIfNeeded() error {
	_, err := game.database.Exec("CREATE TABLE IF NOT EXISTS systemschema(systemid TEXT PRIMARY KEY, versionnumber INTEGER)")
	return err
}

func (game *Game) GetSchemaVersionNumber(systemid string) (int, error) {
	var versionnumber int
	err := game.database.QueryRow("SELECT versionnumber FROM systemschema WHERE systemid = ?", systemid).Scan(&versionnumber)
	return versionnumber, err
}

func (game *Game) SetSchemaVersionNumber(systemid string, versionnumber int) error {
	_, err := game.database.Exec("INSERT OR REPLACE INTO systemschema(systemid,versionnumber) VALUES(?, ?)", systemid, versionnumber)
	return err
}
