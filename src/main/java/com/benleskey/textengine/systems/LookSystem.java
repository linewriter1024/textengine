package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LookSystem extends SingletonGameSystem {
	private PreparedStatement addLookStatement;
	private PreparedStatement getSeenLooksStatement;
	private PreparedStatement getSeesFromContainer;
	private PreparedStatement resetInstantSee;
	private PreparedStatement insertInstantSee;
	private PreparedStatement updateUnseenLooks;
	private PreparedStatement updateSeenLooks;
	private PreparedStatement insertSeenLooks;

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
					s.executeUpdate("CREATE TABLE entity_see_instant(entity_id INTEGER, look_seen_id INTEGER, method TEXT)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create look system tables", e);
			}

			getSchema().setVersionNumber(1);
		}

		try {
			addLookStatement = game.db().prepareStatement("INSERT INTO entity_look (look_id, entity_id, type, description) VALUES (?, ?, ?, ?)");
			getSeenLooksStatement = game.db().prepareStatement("SELECT l.look_id, l.entity_id, l.type, l.description FROM entity_look l INNER JOIN entity_see s ON s.look_seen_id = l.look_id WHERE s.entity_id = ? AND (s.end_time IS NULL OR s.end_time <= ?)");
			resetInstantSee = game.db().prepareStatement("DELETE FROM entity_see_instant WHERE entity_id = ?");
			insertInstantSee = game.db().prepareStatement("INSERT INTO entity_see_instant (entity_id, look_seen_id, method) VALUES (?, ?, ?)");
			getSeesFromContainer = game.db().prepareStatement("SELECT l.look_id FROM entity_look l WHERE l.entity_id IN (SELECT ? UNION SELECT r.receiver_id FROM entity_relationship r WHERE r.provider_id = ? AND r.verb = '" + RelationshipSystem.R_CONTAINS + "')");
			updateUnseenLooks = game.db().prepareStatement("UPDATE entity_see SET end_time = ? WHERE EXISTS ")
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

	private Set<Entity> getRecursiveContains(Entity contained, DTime until, Set<Entity> seenEntities) throws DatabaseException {
		Set<Entity> entities = new HashSet<>(Set.of(contained));
		RelationshipSystem relationshipSystem = game.getSystem(RelationshipSystem.class);

		List<RelationshipDescriptor> containingRelationships = relationshipSystem.getProvidingRelationships(contained, RelationshipSystem.R_CONTAINS, until);
		for(RelationshipDescriptor rd : containingRelationships) {
			if(!seenEntities.contains(rd.getProvider())) {
				seenEntities.add(rd.getProvider());
				entities.addAll(getRecursiveContains(rd.getProvider(), until, seenEntities));
			}
		}

		return entities;
	}

	public synchronized void updateSee(Entity looker) throws DatabaseException {
		try {
			WorldSystem worldSystem = game.getSystem(WorldSystem.class);
			DTime currentTime = worldSystem.getCurrentTime();

			// Get all seen looks.
			resetInstantSee.setLong(1, looker.getId());
			resetInstantSee.executeUpdate();

			// First we find everything that we are in...
			Set<Entity> containers = getRecursiveContains(looker, currentTime, new HashSet<>());

			// Then we find everything that we can see as part of what we are in...
			for(Entity container : containers) {
				getSeesFromContainer.setLong(1, container.getId());
				getSeesFromContainer.setLong(2, container.getId());

				try(ResultSet rs = getSeesFromContainer.executeQuery()){
					Look look = new Look(rs.getLong(1), game);

					insertInstantSee.setLong(1, looker.getId());
					insertInstantSee.setLong(2, look.getId());
					insertInstantSee.setString(3, "basic");
					insertInstantSee.addBatch();
				}

				insertInstantSee.executeBatch();
			}

			// Now that we have a table of everything we can see in this instant...

			// End any sees that are no longer seen.
			updateUnseenLooks.setLong(1, currentTime.getRaw());
			updateUnseenLooks.setLong(2, looker.getId());
			updateUnseenLooks.executeUpdate();

			// Update any sees that are still seen.
			updateSeenLooks.setLong(1, looker.getId());
			updateSeenLooks.execute();

			// Insert any new sees.
			insertSeenLooks.setLong(1, looker.getId());
			insertSeenLooks.executeUpdate();
		}
		catch(SQLException e) {
			throw new DatabaseException("Unable to update sees for " + looker, e);
		}
	}

	public synchronized List<LookDescriptor> getSeenLooks(Entity looker) throws DatabaseException {
		try {
			updateSee(looker);

			WorldSystem worldSystem = game.getSystem(WorldSystem.class);
			DTime currentTime = worldSystem.getCurrentTime();

			List<LookDescriptor> looks = new ArrayList<>();
			getSeenLooksStatement.setLong(1, looker.getId());
			getSeenLooksStatement.setLong(2, currentTime.getRaw());
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
