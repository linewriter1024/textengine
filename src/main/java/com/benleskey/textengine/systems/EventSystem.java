package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.BaseReference;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.FullEvent;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class EventSystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType etCancel;
	private WorldSystem worldSystem;
	private UniqueTypeSystem uniqueTypeSystem;
	private PreparedStatement addEventStatement;

	public EventSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate(
							"CREATE TABLE event(event_order INTEGER PRIMARY KEY, event_id INTEGER, type INTEGER, time INTEGER, reference INTEGER)");
					// Index for finding events by reference (action lookups)
					s.executeUpdate(
							"CREATE INDEX idx_event_reference ON event(reference)");
					// Index for cancel event lookups (type + reference for NOT IN subquery)
					s.executeUpdate(
							"CREATE INDEX idx_event_type_reference ON event(type, reference)");
					// Index for time-based queries
					s.executeUpdate(
							"CREATE INDEX idx_event_type_time ON event(type, time)");
					// Composite index for the common valid events subquery pattern
					s.executeUpdate(
							"CREATE INDEX idx_event_type_time_ref ON event(type, time, reference)");
					// Index optimized for correlated subquery with reference equality
					s.executeUpdate(
							"CREATE INDEX idx_event_type_ref_time ON event(type, reference, time)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create event system tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		try {
			addEventStatement = game.db()
					.prepareStatement("INSERT INTO event (event_id, type, time, reference) VALUES (?, ?, ?, ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare look statements", e);
		}

		worldSystem = game.getSystem(WorldSystem.class);
		uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);

		etCancel = uniqueTypeSystem.getType("event_cancel");
	}

	@SuppressWarnings("null") // Generic type T will never be null
	public synchronized <T extends Reference> FullEvent<T> addEvent(UniqueType type, DTime time, T reference)
			throws DatabaseException {
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

	/**
	 * Cancel an event by creating a cancel-event.
	 * 
	 * @param eventId The ID of the event to cancel
	 */
	public synchronized void cancelEvent(long eventId) {
		addEventNow(etCancel, new BaseReference(eventId, game));
	}

	/**
	 * Cancel all events of a specific type that reference a specific subject.
	 * 
	 * @param eventType The type of events to cancel
	 * @param reference The reference (subject) of the events to cancel
	 * @param when      The time at which to find and cancel events
	 */
	public synchronized void cancelEventsByTypeAndReference(UniqueType eventType, Reference reference, DTime when) {
		try {
			// Find all valid (non-canceled) events of this type with this reference
			// We need to select event_id, not reference, so we use a custom query
			PreparedStatement findEventsStatement = game.db().prepareStatement(
					"SELECT event.event_id FROM event WHERE event.type = ? AND event.reference = ? AND event.time <= ? "
							+ "AND event.event_id NOT IN (SELECT event_cancel.reference FROM event AS event_cancel WHERE event_cancel.type = ? AND event_cancel.time <= ?)");
			findEventsStatement.setLong(1, eventType.type());
			findEventsStatement.setLong(2, reference.getId());
			findEventsStatement.setLong(3, when.raw());
			findEventsStatement.setLong(4, etCancel.type());
			findEventsStatement.setLong(5, when.raw());

			try (var rs = findEventsStatement.executeQuery()) {
				while (rs.next()) {
					cancelEvent(rs.getLong(1));
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException(
					String.format("Could not cancel events of type %s for reference %s", eventType, reference), e);
		}
	}

	public String getValidEventsSubquery(String reference) {
		// Returns a subquery that finds the most recent non-canceled event for a given
		// reference
		// An event is canceled if there exists a cancel-event whose event_id matches
		// this event's event_id (the cancel-event's reference points to the event_id)
		return "(SELECT event.reference FROM event WHERE event.type = ? AND event.time <= ? AND event.reference = "
				+ reference
				+ " AND event.event_id NOT IN (SELECT event_cancel.reference FROM event AS event_cancel WHERE event_cancel.type = ? AND event_cancel.time <= ?) ORDER BY event.event_order DESC LIMIT 1)";
	}

	/**
	 * @param statement
	 * @param offset
	 * @return the parameter number AFTER the last subquery parameter
	 */
	public int setValidEventsSubqueryParameters(PreparedStatement statement, int offset, UniqueType eventType,
			DTime when) {
		try {
			statement.setLong(offset++, eventType.type());
			statement.setLong(offset++, when.raw());
			statement.setLong(offset++, etCancel.type());
			statement.setLong(offset++, when.raw());
		} catch (SQLException e) {
			throw new DatabaseException(
					String.format("Could not set valid events subquery parameters for %s; %s", eventType, when), e);
		}
		return offset;
	}
}
