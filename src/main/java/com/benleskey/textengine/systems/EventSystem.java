package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.FullEvent;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class EventSystem extends SingletonGameSystem {
	private WorldSystem worldSystem;
	private PreparedStatement addEventStatement;

	public EventSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE event(event_order INTEGER PRIMARY KEY, event_id INTEGER, type INTEGER, time INTEGER, reference INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create event system tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		try {
			addEventStatement = game.db().prepareStatement("INSERT INTO event (event_id, type, time, reference) VALUES (?, ?, ?, ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare look statements", e);
		}

		worldSystem = game.getSystem(WorldSystem.class);
	}

	public synchronized <T extends Reference> FullEvent<T> addEvent(UniqueType type, DTime time, T reference) throws DatabaseException {
		try {
			long id = game.getNewGlobalId();
			addEventStatement.setLong(1, id);
			addEventStatement.setLong(2, type.type());
			addEventStatement.setLong(3, time.raw());
			addEventStatement.setLong(4, reference.getId());
			addEventStatement.execute();
			return new FullEvent<>(id, reference, game);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to create new event", e);
		}
	}

	public synchronized <T extends Reference> FullEvent<T> addEventNow(UniqueType type, T reference) {
		return addEvent(type, worldSystem.getCurrentTime(), reference);
	}
}
