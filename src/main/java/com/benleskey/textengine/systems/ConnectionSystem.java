package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConnectionSystem manages directional connections between places.
 * Connections are stored as relationship events, allowing dynamic
 * exit creation and temporal tracking of connections.
 */
public class ConnectionSystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType rvConnectsTo;
	private EventSystem eventSystem;
	private RelationshipSystem relationshipSystem;

	private PreparedStatement getConnectionsStatement;

	public ConnectionSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() {
		eventSystem = game.getSystem(EventSystem.class);
		relationshipSystem = game.getSystem(RelationshipSystem.class);

		UniqueTypeSystem uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		rvConnectsTo = uniqueTypeSystem.getType("relationship_connects_to");

		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No additional tables needed - we use relationship system + properties
			getSchema().setVersionNumber(1);
		}

		try {
			// Get all connections from a place
			getConnectionsStatement = game.db().prepareStatement(
					"SELECT relationship_id, receiver_id FROM entity_relationship " +
							"WHERE provider_id = ? AND relationship_verb = ? AND relationship_id IN " +
							eventSystem.getValidEventsSubquery("entity_relationship.relationship_id") +
							" ORDER BY relationship_id");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare connection statements", e);
		}
	}

	/**
	 * Create a one-way connection from one place to another.
	 * 
	 * @param from The place the connection starts from
	 * @param to   The place the connection leads to
	 * @return The relationship event representing this connection
	 */
	public synchronized FullEvent<Relationship> connect(Entity from, Entity to) {
		return relationshipSystem.add(from, to, rvConnectsTo);
	}

	/**
	 * Create a bidirectional connection between two places.
	 * 
	 * @param place1 First place
	 * @param place2 Second place
	 */
	public synchronized void connectBidirectional(Entity place1, Entity place2) {
		connect(place1, place2);
		connect(place2, place1);
	}

	/**
	 * Get all connections from a place at a specific time.
	 * 
	 * @param from The place to get connections from
	 * @param when The time to query
	 * @return List of connection descriptors
	 */
	public synchronized List<ConnectionDescriptor> getConnections(Entity from, DTime when) {
		try {
			List<ConnectionDescriptor> connections = new ArrayList<>();
			getConnectionsStatement.setLong(1, from.getId());
			getConnectionsStatement.setLong(2, rvConnectsTo.type());
			eventSystem.setValidEventsSubqueryParameters(getConnectionsStatement, 3,
					relationshipSystem.etEntityRelationship, when);

			try (ResultSet rs = getConnectionsStatement.executeQuery()) {
				EntitySystem entitySystem = game.getSystem(EntitySystem.class);
				while (rs.next()) {
					Relationship rel = new Relationship(rs.getLong(1), game);
					Entity to = entitySystem.get(rs.getLong(2));

					connections.add(ConnectionDescriptor.builder()
							.from(from)
							.to(to)
							.relationship(rel)
							.build());
				}
			}
			return connections;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get connections from " + from, e);
		}
	}
}
