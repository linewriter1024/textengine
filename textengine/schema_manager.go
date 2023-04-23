package textengine

func (game *Game) CreateSchemaTableIfNeeded() error {
	_, err := game.database.Exec("CREATE TABLE IF NOT EXISTS systemschema(systemid TEXT PRIMARY KEY, versionnumber INTEGER)");
	return err
}

func (game *Game) GetSchemaVersionNumber(systemid string) (int, error) {
}

func (game *Game) SetSchemaVersionNumber(systemid string, number int) error {

}
