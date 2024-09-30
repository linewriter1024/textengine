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

public class LookSystem extends SingletonGameSystem {
	private PreparedStatement addLookStatement;
	private EventSystem eventSystem;
	public UniqueType etEntityLook;
	public UniqueType etEntitySee;

	public LookSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
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

		try {
			addLookStatement = game.db().prepareStatement("INSERT INTO entity_look (look_id, entity_id, type, description) VALUES (?, ?, ?, ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare look statements", e);
		}

		eventSystem = game.getSystem(EventSystem.class);

		UniqueTypeSystem uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		etEntityLook = uniqueTypeSystem.getType("event_entity_look");
		etEntitySee = uniqueTypeSystem.getType("event_entity_see");
	}

	public synchronized FullEvent<Look> addLook(Entity entity, String type, String description) throws DatabaseException {
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

	/*
	private Set<Entity> getRecursiveContains(Entity contained, DTime until, Set<Entity> seenEntities) throws DatabaseException {
		Set<Entity> entities = new HashSet<>(Set.of(contained));
		RelationshipSystem relationshipSystem = game.getSystem(RelationshipSystem.class);

		List<RelationshipDescriptor> containingRelationships = relationshipSystem.getProvidingRelationships(contained, RelationshipSystem.R_CONTAINS, until);
		for (RelationshipDescriptor rd : containingRelationships) {
			if (!seenEntities.contains(rd.getProvider())) {
				seenEntities.add(rd.getProvider());
				entities.addAll(getRecursiveContains(rd.getProvider(), until, seenEntities));
			}
		}

		return entities;
	}
	 */

	public synchronized List<LookDescriptor> getSeenLooks(Entity looker) throws DatabaseException {
			List<LookDescriptor> looks = new ArrayList<>();
			/*
			getSeenLooksStatement.setLong(1, looker.getId());
			getSeenLooksStatement.setLong(2, currentTime.raw());
			try (ResultSet rs = getSeenLooksStatement.executeQuery()) {
				while (rs.next()) {
					LookDescriptor lookDescriptor = LookDescriptor.builder()
						.look(new Look(rs.getLong(1), game))
						.entity(new Entity(rs.getLong(2), game))
						.type(rs.getString(3))
						.description(rs.getString(4))
						.build();
					looks.add(lookDescriptor);
				}
			}
			 */
			return looks;
	}
}
