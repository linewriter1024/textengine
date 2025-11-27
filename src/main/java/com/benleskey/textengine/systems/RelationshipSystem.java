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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RelationshipSystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType etEntityRelationship;
	public UniqueType rvContains;
	private EntitySystem entitySystem;
	private EventSystem eventSystem;
	private PreparedStatement addStatement;
	private PreparedStatement getProviderStatement;
	private PreparedStatement getReceiverStatement;

	public RelationshipSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_relationship(relationship_id INTEGER PRIMARY KEY, provider_id INTEGER, receiver_id INTEGER, relationship_verb INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity relationship table", e);
			}
			getSchema().setVersionNumber(1);
		}

		eventSystem = game.getSystem(EventSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_relationship (relationship_id, provider_id, receiver_id, relationship_verb) VALUES (?, ?, ?, ?)");
			getProviderStatement = game.db().prepareStatement("SELECT relationship_id, provider_id FROM entity_relationship WHERE receiver_id = ? AND relationship_verb = ? AND relationship_id IN " + eventSystem.getValidEventsSubquery("entity_relationship.relationship_id") + " ORDER BY relationship_id");
			getReceiverStatement = game.db().prepareStatement("SELECT relationship_id, receiver_id FROM entity_relationship WHERE provider_id = ? AND relationship_verb = ? AND relationship_id IN " + eventSystem.getValidEventsSubquery("entity_relationship.relationship_id") + " ORDER BY relationship_id");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare relationship statements", e);
		}

		UniqueTypeSystem uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		etEntityRelationship = uniqueTypeSystem.getType("event_entity_relationship");
		rvContains = uniqueTypeSystem.getType("relationship_contains");
	}

	public synchronized FullEvent<Relationship> add(Entity provider, Entity receiver, UniqueType verb) throws DatabaseException {
		try {
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, provider.getId());
			addStatement.setLong(3, receiver.getId());
			addStatement.setLong(4, verb.type());
			addStatement.executeUpdate();
			return eventSystem.addEventNow(etEntityRelationship, new Relationship(newId, game));
		} catch (SQLException e) {
			throw new DatabaseException(String.format("Unable to create relationship (%s) %s (%s)", provider, verb, receiver), e);
		}
	}

	private synchronized Set<Entity> getProvidingEntitiesRecursive(Entity receiver, UniqueType verb, DTime when, Set<Entity> seenEntities) {
		Set<Entity> entities = new HashSet<>(Set.of(receiver));

		List<RelationshipDescriptor> containingRelationships = getProvidingRelationships(receiver, verb, when);
		for (RelationshipDescriptor rd : containingRelationships) {
			if (!seenEntities.contains(rd.getProvider())) {
				seenEntities.add(rd.getProvider());
				entities.addAll(getProvidingEntitiesRecursive(rd.getProvider(), verb, when, seenEntities));
			}
		}

		return entities;
	}

	public synchronized Set<Entity> getProvidingEntitiesRecursive(Entity receiver, UniqueType verb, DTime when) {
		return getProvidingEntitiesRecursive(receiver, verb, when, new HashSet<>());
	}

	public synchronized List<RelationshipDescriptor> getProvidingRelationships(Entity receiver, UniqueType verb, DTime when) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getProviderStatement.setLong(1, receiver.getId());
			getProviderStatement.setLong(2, verb.type());
			eventSystem.setValidEventsSubqueryParameters(getProviderStatement, 3, etEntityRelationship, when);
			try (ResultSet rs = getProviderStatement.executeQuery()) {
				while (rs.next()) {
					rds.add(RelationshipDescriptor.builder()
						.relationship(new Relationship(rs.getLong(1), game))
						.receiver(receiver)
						.provider(entitySystem.get(rs.getLong(2)))
						.verb(verb)
						.build());
				}
			}
			return rds;
		} catch (SQLException e) {
			throw new DatabaseException("Could not get providing entities for " + receiver + " " + verb + " at game time " + when, e);
		}
	}

	public synchronized List<RelationshipDescriptor> getReceivingRelationships(Entity provider, UniqueType verb, DTime when) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getReceiverStatement.setLong(1, provider.getId());
			getReceiverStatement.setLong(2, verb.type());
			eventSystem.setValidEventsSubqueryParameters(getReceiverStatement, 3, etEntityRelationship, when);
			try (ResultSet rs = getReceiverStatement.executeQuery()) {
				while (rs.next()) {
					rds.add(RelationshipDescriptor.builder()
						.relationship(new Relationship(rs.getLong(1), game))
						.provider(provider)
						.receiver(entitySystem.get(rs.getLong(2)))
						.verb(verb)
						.build());
				}
			}
			return rds;
		} catch (SQLException e) {
			throw new DatabaseException("Could not get receiving entities for " + provider + " " + verb + " up to game time " + when, e);
		}
	}
}
