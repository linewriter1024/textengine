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
	public UniqueType etCancel;
	private WorldSystem worldSystem;
	private UniqueTypeSystem uniqueTypeSystem;
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
		uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);

		etCancel = uniqueTypeSystem.getType("event_cancel");
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

	public String getValidEventsSubquery(String reference) {
		return "(SELECT event.reference FROM event WHERE event.type = ? AND event.time <= ? AND event.reference = " + reference + " AND event.event_id NOT IN (SELECT event_cancel.reference FROM event AS event_cancel WHERE event_cancel.type = ? AND event_cancel.time <= ? AND event_cancel.event_order > event.event_order) ORDER BY event.event_order DESC LIMIT 1)";
	}

	/**
	 * @param statement
	 * @param offset
	 * @return the parameter number AFTER the last subquery parameter
	 */
	public int setValidEventsSubqueryParameters(PreparedStatement statement, int offset, UniqueType eventType, DTime when) {
		try {
			statement.setLong(offset++, eventType.type());
			statement.setLong(offset++, when.raw());
			statement.setLong(offset++, etCancel.type());
			statement.setLong(offset++, when.raw());
		} catch (SQLException e) {
			throw new DatabaseException(String.format("Could not set valid events subquery parameters for %s; %s", eventType, when), e);
		}
		return offset;
	}
}
