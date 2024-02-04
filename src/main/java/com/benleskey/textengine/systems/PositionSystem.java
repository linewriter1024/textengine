package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.Entity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class PositionSystem extends SingletonGameSystem {
	private PreparedStatement getScaleStatement;
	private PreparedStatement setScaleStatement;

	public PositionSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_position_scale(entity_id INTEGER PRIMARY KEY, scale TEXT)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity position scale table", e);
			}
			getSchema().setVersionNumber(1);
		}

		try {
			getScaleStatement = game.db().prepareStatement("SELECT scale FROM entity_position_scale WHERE entity_id = ?");
			setScaleStatement = game.db().prepareStatement("INSERT OR REPLACE INTO entity_position_scale (entity_id, scale) VALUES (?, ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Could not prepare statements for position system", e);
		}
	}

	public synchronized void setScale(Entity entity, String scale) throws DatabaseException {
		try {
			setScaleStatement.setLong(1, entity.getId());
			setScaleStatement.setString(2, scale);
			setScaleStatement.executeUpdate();
		} catch (SQLException e) {
			throw new DatabaseException("Cannot set scale for entity " + entity, e);
		}
	}

	public synchronized Optional<String> getScale(Entity entity) throws DatabaseException {
		try {
			getScaleStatement.setLong(1, entity.getId());
			try (ResultSet rs = getScaleStatement.executeQuery()) {
				if (rs.next()) {
					return Optional.of(rs.getString(1));
				} else {
					return Optional.empty();
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Cannot get scale for entity " + entity, e);
		}
	}
}
