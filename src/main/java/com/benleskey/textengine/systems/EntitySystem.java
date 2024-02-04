package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.Entity;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class EntitySystem extends SingletonGameSystem {
	private PreparedStatement addStatement;

	public EntitySystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity(entity_id INTEGER PRIMARY KEY)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity table", e);
			}
			getSchema().setVersionNumber(1);
		}

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity (entity_id) VALUES (?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare entity statements", e);
		}
	}

	public synchronized Entity add() throws DatabaseException {
		try {
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.executeUpdate();
			return new Entity(newId, game);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to add entity", e);
		}
	}
}
