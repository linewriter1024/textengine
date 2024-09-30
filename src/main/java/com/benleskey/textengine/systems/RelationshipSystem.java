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
import java.util.List;

public class RelationshipSystem extends SingletonGameSystem {
	public UniqueType etEntityRelationship;
	public UniqueType rvContains;
	private EventSystem eventSystem;
	private PreparedStatement addStatement;
	private PreparedStatement getProviderStatement;
	private PreparedStatement getReceiverStatement;

	public RelationshipSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
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

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_relationship (relationship_id, provider_id, receiver_id, relationship_verb) VALUES (?, ?, ?, ?)");
			getProviderStatement = game.db().prepareStatement("SELECT relationship_id, provider_id FROM entity_relationship WHERE receiver_id = ? AND relationship_verb = ?");
			getReceiverStatement = game.db().prepareStatement("SELECT relationship_id, receiver_id FROM entity_relationship WHERE provider_id = ? AND relationship_verb = ?");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare relationship statements", e);
		}

		eventSystem = game.getSystem(EventSystem.class);

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

	public synchronized List<RelationshipDescriptor> getProvidingRelationships(Entity receiver, UniqueType verb, DTime until) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getProviderStatement.setLong(1, receiver.getId());
			getProviderStatement.setLong(2, verb.type());
			getProviderStatement.setLong(3, until.raw());
			try (ResultSet rs = getProviderStatement.executeQuery()) {
				while (rs.next()) {
					rds.add(RelationshipDescriptor.builder()
						.relationship(new Relationship(rs.getLong(1), game))
						.receiver(receiver)
						.provider(new Entity(rs.getLong(2), game))
						.verb(verb)
						.build());
				}
			}
			return rds;
		} catch (SQLException e) {
			throw new DatabaseException("Could not get providing entities for " + receiver + " " + verb + " up to game time " + until, e);
		}
	}

	public synchronized List<RelationshipDescriptor> getReceivingRelationships(Entity provider, UniqueType verb, DTime until) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getReceiverStatement.setLong(1, provider.getId());
			getReceiverStatement.setLong(2, verb.type());
			getReceiverStatement.setLong(3, until.raw());
			try (ResultSet rs = getReceiverStatement.executeQuery()) {
				while (rs.next()) {
					rds.add(RelationshipDescriptor.builder()
						.relationship(new Relationship(rs.getLong(1), game))
						.provider(provider)
						.receiver(new Entity(rs.getLong(2), game))
						.verb(verb)
						.build());
				}
			}
			return rds;
		} catch (SQLException e) {
			throw new DatabaseException("Could not get receiving entities for " + provider + " " + verb + " up to game time " + until, e);
		}
	}
}
