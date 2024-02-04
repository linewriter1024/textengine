package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Look;
import com.benleskey.textengine.model.LookDescriptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class LookSystem extends SingletonGameSystem {
	private PreparedStatement addLookStatement;
	private PreparedStatement getSeenLooksStatement;

	public LookSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_look(look_id INTEGER PRIMARY KEY, entity_id INTEGER, type TEXT, description TEXT, start_time INTEGER, end_time INTEGER)");
					s.executeUpdate("CREATE TABLE entity_see(see_id INTEGER PRIMARY KEY, entity_id INTEGER, look_seen_id INTEGER, method TEXT, start_time INTEGER, end_time INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create look system tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		try {
			addLookStatement = game.db().prepareStatement("INSERT INTO entity_look (look_id, entity_id, type, description) VALUES (?, ?, ?, ?)");
			getSeenLooksStatement = game.db().prepareStatement("SELECT l.look_id, l.entity_id, l.type, l.description FROM entity_look l INNER JOIN entity_see s ON s.look_seen_id = l.look_id WHERE s.entity_id = ? AND s.end_time IS NULL");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare look statements", e);
		}
	}

	public synchronized Look addLook(Entity entity, String type, String description) throws DatabaseException {
		try {
			long id = game.getNewGlobalId();
			addLookStatement.setLong(1, id);
			addLookStatement.setLong(2, entity.getId());
			addLookStatement.setString(3, type);
			addLookStatement.setString(4, description);
			addLookStatement.executeUpdate();
			return new Look(id, game);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to create new look", e);
		}
	}

	public synchronized List<LookDescriptor> getSeenLooks(Entity looker) throws DatabaseException {
		try {
			List<LookDescriptor> looks = new ArrayList<>();
			getSeenLooksStatement.setLong(1, looker.getId());
			try(ResultSet rs = getSeenLooksStatement.executeQuery()) {
				while(rs.next()) {
					LookDescriptor lookDescriptor = LookDescriptor.builder()
							.look(new Look(rs.getLong(1), game))
							.entity(new Entity(rs.getLong(2), game))
							.type(rs.getString(3))
							.description(rs.getString(4))
							.build();
					looks.add(lookDescriptor);
				}
			}
			return looks;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get seen looks for " + looker, e);
		}
	}
}
