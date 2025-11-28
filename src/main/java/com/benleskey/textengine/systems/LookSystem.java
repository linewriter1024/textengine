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

public class LookSystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType etEntityLook;
	private PreparedStatement addLookStatement;
	private PreparedStatement getCurrentLookStatement;
	private EntitySystem entitySystem;
	private EventSystem eventSystem;
	private WorldSystem worldSystem;
	private RelationshipSystem relationshipSystem;

	public LookSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_look(look_id INTEGER PRIMARY KEY, entity_id INTEGER, type TEXT, description TEXT)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create look system tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		eventSystem = game.getSystem(EventSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);

		try {
			addLookStatement = game.db().prepareStatement("INSERT INTO entity_look (look_id, entity_id, type, description) VALUES (?, ?, ?, ?)");
			getCurrentLookStatement = game.db().prepareStatement("SELECT entity_look.look_id, entity_look.entity_id, entity_look.type, entity_look.description FROM entity_look WHERE entity_look.entity_id = ? AND entity_look.look_id IN " + eventSystem.getValidEventsSubquery("entity_look.look_id"));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare look statements", e);
		}

		worldSystem = game.getSystem(WorldSystem.class);
		relationshipSystem = game.getSystem(RelationshipSystem.class);

		UniqueTypeSystem uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		etEntityLook = uniqueTypeSystem.getType("event_entity_look");
	}

	public synchronized FullEvent<Look> addLook(Entity entity, String type, String description) {
		try {
			long id = game.getNewGlobalId();
			addLookStatement.setLong(1, id);
			addLookStatement.setLong(2, entity.getId());
			addLookStatement.setString(3, type);
			addLookStatement.setString(4, description);
			addLookStatement.executeUpdate();
			return eventSystem.addEventNow(etEntityLook, new Look(id, game));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to create new look", e);
		}
	}

	public synchronized List<LookDescriptor> getLooksFromEntity(Entity looker, DTime when) {
		try {
			getCurrentLookStatement.setLong(1, looker.getId());
			eventSystem.setValidEventsSubqueryParameters(getCurrentLookStatement, 2, etEntityLook, when);
			try (ResultSet rs = getCurrentLookStatement.executeQuery()) {
				List<LookDescriptor> result = new ArrayList<>();
				while (rs.next()) {
					result.add(LookDescriptor.builder()
						.look(new Look(rs.getLong(1), game))
						.entity(entitySystem.get(rs.getLong(2)))
						.type(rs.getString(3))
						.description(rs.getString(4))
						.build());
				}
				return result;
			}
		} catch (SQLException e) {
			throw new DatabaseException(String.format("Unable to get looks from entity %s at %s", looker, when), e);
		}
	}

	/**
	 * Get comprehensive environment observation for an entity.
	 * Used by both player look commands and NPC AI decision-making.
	 * Returns structured data about current location, exits, items, and nearby entities.
	 */
	public static class LookEnvironment {
		public final Entity currentLocation;
		public final List<LookDescriptor> locationLooks;
		public final List<Entity> exits;
		public final List<Entity> itemsHere;
		public final List<Entity> itemsCarried;
		public final List<Entity> actorsHere;
		public final List<Entity> distantLandmarks;
		
		public LookEnvironment(Entity currentLocation, List<LookDescriptor> locationLooks,
		                       List<Entity> exits, List<Entity> itemsHere, List<Entity> itemsCarried,
		                       List<Entity> actorsHere, List<Entity> distantLandmarks) {
			this.currentLocation = currentLocation;
			this.locationLooks = locationLooks;
			this.exits = exits;
			this.itemsHere = itemsHere;
			this.itemsCarried = itemsCarried;
			this.actorsHere = actorsHere;
			this.distantLandmarks = distantLandmarks;
		}
	}
	
	/**
	 * Observe the environment from an entity's perspective.
	 * This is the shared implementation used by both player look commands and NPC AI.
	 * 
	 * @param observer The entity observing their environment
	 * @return LookEnvironment with all observable information, or null if observer has no location
	 */
	public LookEnvironment getLookEnvironment(Entity observer) {
		DTime when = worldSystem.getCurrentTime();
		
		// Get current location
		var containers = relationshipSystem.getProvidingRelationships(observer, relationshipSystem.rvContains, when);
		if (containers.isEmpty()) {
			return null; // Observer has no location
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get location descriptions
		List<LookDescriptor> locationLooks = getLooksFromEntity(currentLocation, when);
		
		// Get available exits
		ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
		List<Entity> exits = cs.getConnections(currentLocation, when).stream()
			.map(cd -> cd.getTo())
			.toList();
		
		// Get items at current location (all items - filtering happens in calling code)
		List<Entity> itemsHere = relationshipSystem.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, when)
			.stream()
			.map(rd -> rd.getReceiver())
			.filter(e -> e instanceof com.benleskey.textengine.entities.Item)
			.toList();
		
		// Get items carried by observer
		List<Entity> itemsCarried = relationshipSystem.getReceivingRelationships(observer, relationshipSystem.rvContains, when)
			.stream()
			.map(rd -> rd.getReceiver())
			.filter(e -> e instanceof com.benleskey.textengine.entities.Item)
			.toList();
		
		// Get other actors at current location
		List<Entity> actorsHere = relationshipSystem.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, when)
			.stream()
			.map(rd -> rd.getReceiver())
			.filter(e -> e instanceof com.benleskey.textengine.entities.Actor)
			.filter(e -> !e.equals(observer)) // Don't include self
			.toList();
		
		// Get distant landmarks (visible but not adjacent)
		VisibilitySystem vs = game.getSystem(VisibilitySystem.class);
		List<Entity> distantLandmarks = vs.getVisibleEntities(observer).stream()
			.filter(vd -> vd.getDistanceLevel() == VisibilitySystem.VisibilityLevel.DISTANT)
			.map(vd -> vd.getEntity())
			.toList();
		
		return new LookEnvironment(currentLocation, locationLooks, exits, itemsHere,
		                           itemsCarried, actorsHere, distantLandmarks);
	}
}
