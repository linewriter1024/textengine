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

	public synchronized List<LookDescriptor> getSeenLooks(Entity looker) {
		DTime when = worldSystem.getCurrentTime();

		// Get everything that currently contains the looker.
		Set<Entity> containingEntities = relationshipSystem.getProvidingEntitiesRecursive(looker, relationshipSystem.rvContains, when);

		// Get those container's direct current children.
		Set<Entity> children = containingEntities
			.stream()
			.flatMap(entity -> relationshipSystem.getReceivingRelationships(entity, relationshipSystem.rvContains, when).stream())
			.map(RelationshipDescriptor::getReceiver)
			.collect(Collectors.toSet());

		// Get containers and children all in one.
		Set<Entity> allEntities = new HashSet<>();
		allEntities.addAll(containingEntities);
		allEntities.addAll(children);

		// Get all looks from all entities.
		return allEntities
			.stream()
			.flatMap(entity -> getLooksFromEntity(entity, when).stream())
			.toList();
	}
}
