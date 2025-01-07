package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

public class EntityTagSystem extends SingletonGameSystem {
	private PreparedStatement addStatement;
	private PreparedStatement findByTagStatement;
	private EntitySystem entitySystem;
	private EventSystem eventSystem;
	private UniqueType etEntityTag;

	public EntityTagSystem(Game game) {
		super(game);
	}

	@Override
	public void initialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_tag(entity_tag_id INTEGER PRIMARY KEY, entity_id INTEGER, entity_tag_type INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity tag table", e);
			}
			getSchema().setVersionNumber(1);
		}

		eventSystem = game.getSystem(EventSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_tag (entity_tag_id, entity_id, entity_tag_type) VALUES (?, ?, ?)");
			findByTagStatement = game.db().prepareStatement("SELECT entity_id FROM entity_tag WHERE entity_tag_type = ? AND entity_tag_id IN " + eventSystem.getValidEventsSubquery("entity_tag_id"));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare entity tag statements", e);
		}

		etEntityTag = game.getSystem(UniqueTypeSystem.class).getType("entity_tag");
	}

	public synchronized Reference add(Entity entity, UniqueType tagType) throws DatabaseException {
		try {
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, entity.getId());
			addStatement.setLong(3, tagType.type());
			addStatement.executeUpdate();
			return new Reference(newId, game);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to add entity tag", e);
		}
	}

	public synchronized Set<Entity> findEntitiesByTag(UniqueType tag, DTime when) {
		try {
			findByTagStatement.setLong(1, tag.type());
			eventSystem.setValidEventsSubqueryParameters(findByTagStatement, 2, etEntityTag, when);

			Set<Entity> entities = new HashSet<>();
			try(ResultSet rs = findByTagStatement.executeQuery()) {
				while (rs.next()) {
					entities.add(entitySystem.get(rs.getLong(1)));
				}
			}
			return entities;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to find entities by tag", e);
		}
	}
}
