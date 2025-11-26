package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ConnectionSystem manages directional connections between places.
 * Connections are stored as properties on relationship events, allowing dynamic
 * exit creation and temporal tracking of connections.
 */
public class ConnectionSystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType rvConnectsTo;
	private EventSystem eventSystem;
	private RelationshipSystem relationshipSystem;
	private PropertiesSubSystem<Long, String, String> exitProperties;
	
	private PreparedStatement getConnectionsStatement;

	public ConnectionSystem(Game game, PropertiesSubSystem<Long, String, String> exitProperties) {
		super(game);
		this.exitProperties = exitProperties;
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
				eventSystem.getValidEventsSubquery("entity_relationship.relationship_id")
			);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare connection statements", e);
		}
	}

	/**
	 * Create a one-way connection from one place to another with a named exit.
	 * @param from The place the connection starts from
	 * @param to The place the connection leads to
	 * @param exitName The name of the exit (e.g., "north", "upstream", "castle")
	 * @return The relationship event representing this connection
	 */
	public synchronized FullEvent<Relationship> connect(Entity from, Entity to, String exitName) {
		FullEvent<Relationship> connection = relationshipSystem.add(from, to, rvConnectsTo);
		
		// Store the exit name as a property on the relationship ID
		exitProperties.set(connection.getReference().getId(), "exit_name", exitName);
		
		return connection;
	}

	/**
	 * Create a bidirectional connection between two places.
	 * @param place1 First place
	 * @param place2 Second place
	 * @param exit1to2 Exit name from place1 to place2
	 * @param exit2to1 Exit name from place2 to place1
	 */
	public synchronized void connectBidirectional(Entity place1, Entity place2, String exit1to2, String exit2to1) {
		connect(place1, place2, exit1to2);
		connect(place2, place1, exit2to1);
	}

	/**
	 * Get all connections from a place at a specific time.
	 * @param from The place to get connections from
	 * @param when The time to query
	 * @return List of connection descriptors with exit names
	 */
	public synchronized List<ConnectionDescriptor> getConnections(Entity from, DTime when) {
		try {
			List<ConnectionDescriptor> connections = new ArrayList<>();
			getConnectionsStatement.setLong(1, from.getId());
			getConnectionsStatement.setLong(2, rvConnectsTo.type());
			eventSystem.setValidEventsSubqueryParameters(getConnectionsStatement, 3, relationshipSystem.etEntityRelationship, when);
			
			try (ResultSet rs = getConnectionsStatement.executeQuery()) {
				EntitySystem entitySystem = game.getSystem(EntitySystem.class);
				while (rs.next()) {
					Relationship rel = new Relationship(rs.getLong(1), game);
					Entity to = entitySystem.get(rs.getLong(2));
					
					// Get the exit name from properties
					String exitName = exitProperties.get(rel.getId(), "exit_name").orElse("unknown");
					
					connections.add(ConnectionDescriptor.builder()
						.from(from)
						.to(to)
						.exitName(exitName)
						.relationship(rel)
						.build());
				}
			}
			return connections;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get connections from " + from, e);
		}
	}

	/**
	 * Find where an exit leads from a given place.
	 * @param from The place to exit from
	 * @param exitName The name of the exit
	 * @param when The time to query
	 * @return The destination entity, if the exit exists
	 */
	public synchronized Optional<Entity> findExit(Entity from, String exitName, DTime when) {
		List<ConnectionDescriptor> connections = getConnections(from, when);
		return connections.stream()
			.filter(c -> c.getExitName().equalsIgnoreCase(exitName))
			.map(ConnectionDescriptor::getTo)
			.findFirst();
	}

	/**
	 * Check if an exit exists from a place.
	 * @param from The place to check
	 * @param exitName The exit name to look for
	 * @param when The time to query
	 * @return True if the exit exists
	 */
	public synchronized boolean hasExit(Entity from, String exitName, DTime when) {
		return findExit(from, exitName, when).isPresent();
	}
}
