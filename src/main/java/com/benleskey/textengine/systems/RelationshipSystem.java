package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.GameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Relationship;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class RelationshipSystem extends GameSystem {
	private PreparedStatement addStatement;

	public RelationshipSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE IF NOT EXISTS entity_relationship(relationship_id INTEGER PRIMARY KEY, provider_id INTEGER, receiver_id INTEGER, relationship_verb TEXT, start_time INTEGER, end_time INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity relationship table", e);
			}
			getSchema().setVersionNumber(1);
		}

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_relationship (relationship_id, provider_id, receiver_id, relationship_verb) VALUES (?, ?, ?, ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare relationship statements", e);
		}
	}

	public Relationship add(Entity provider, Entity receiver, String verb) throws DatabaseException {
		try {
			long newId = game.getNewId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, provider.getId());
			addStatement.setLong(3, receiver.getId());
			addStatement.setString(4, verb);
			addStatement.executeUpdate();
			return new Relationship(newId, game);
		} catch (SQLException e) {
			throw new DatabaseException(String.format("Unable to create relationship (%s) %s (%s)", provider, verb, receiver), e);
		}
	}
}
