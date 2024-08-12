package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Relationship;
import com.benleskey.textengine.model.RelationshipDescriptor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RelationshipSystem extends SingletonGameSystem {
	public static String R_CONTAINS = "contains";
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
					s.executeUpdate("CREATE TABLE entity_relationship(relationship_id INTEGER PRIMARY KEY, provider_id INTEGER, receiver_id INTEGER, relationship_verb TEXT, start_time INTEGER, end_time INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity relationship table", e);
			}
			getSchema().setVersionNumber(1);
		}

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_relationship (relationship_id, provider_id, receiver_id, relationship_verb) VALUES (?, ?, ?, ?)");
			getProviderStatement = game.db().prepareStatement("SELECT relationship_id, provider_id FROM entity_relationship WHERE receiver_id = ? AND relationship_verb = ? AND (end_time IS NULL OR end_time <= ?)");
			getReceiverStatement = game.db().prepareStatement("SELECT relationship_id, receiver_id FROM entity_relationship WHERE provider_id = ? AND relationship_verb = ? AND (end_time IS NULL OR end_time <= ?)");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare relationship statements", e);
		}
	}

	public synchronized Relationship add(Entity provider, Entity receiver, String verb) throws DatabaseException {
		try {
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, provider.getId());
			addStatement.setLong(3, receiver.getId());
			addStatement.setString(4, verb);
			addStatement.executeUpdate();
			return new Relationship(newId, game);
		} catch (SQLException e) {
			throw new DatabaseException(String.format("Unable to create relationship (%s) %s (%s)", provider, verb, receiver), e);
		}
	}

	public synchronized List<RelationshipDescriptor> getProvidingRelationships(Entity receiver, String verb, DTime until) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getProviderStatement.setLong(1, receiver.getId());
			getProviderStatement.setString(2, verb);
			getProviderStatement.setLong(3, until.getRaw());
			try(ResultSet rs = getProviderStatement.executeQuery()) {
				while(rs.next()) {
					rds.add(RelationshipDescriptor.builder()
							.relationship(new Relationship(rs.getLong(1), game))
							.receiver(receiver)
							.provider(new Entity(rs.getLong(2), game))
							.verb(verb)
							.build());
				}
			}
			return rds;
		}
		catch(SQLException e) {
			throw new DatabaseException("Could not get providing entities for " + receiver + " " + verb + " up to game time " + until, e);
		}
	}

	public synchronized List<RelationshipDescriptor> getReceivingRelationships(Entity provider, String verb, DTime until) throws DatabaseException {
		try {
			List<RelationshipDescriptor> rds = new ArrayList<>();
			getProviderStatement.setLong(1, provider.getId());
			getProviderStatement.setString(2, verb);
			getProviderStatement.setLong(3, until.getRaw());
			try(ResultSet rs = getProviderStatement.executeQuery()) {
				while(rs.next()) {
					rds.add(RelationshipDescriptor.builder()
							.relationship(new Relationship(rs.getLong(1), game))
							.provider(provider)
							.receiver(new Entity(rs.getLong(2), game))
							.verb(verb)
							.build());
				}
			}
			return rds;
		}
		catch(SQLException e) {
			throw new DatabaseException("Could not get receiving entities for " + provider + " " + verb + " up to game time " + until, e);
		}
	}
}
