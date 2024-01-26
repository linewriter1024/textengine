package com.benleskey.textengine;

import com.benleskey.textengine.exceptions.DatabaseException;

import java.sql.SQLException;
import java.sql.Statement;

public class EntitySystem extends GameSystem {
	public EntitySystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if(v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE IF NOT EXISTS entity(entity_id TEXT PRIMARY KEY)");
				}
			} catch(SQLException e) {
				throw new DatabaseException("Unable to create entity table", e);
			}
			getSchema().setVersionNumber(1);
		}
	}
}
