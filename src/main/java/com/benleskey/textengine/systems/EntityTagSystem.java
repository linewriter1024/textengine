package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
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

public class EntityTagSystem extends SingletonGameSystem implements OnSystemInitialize {
	private PreparedStatement addStatement;
	private PreparedStatement findByTagStatement;
	private PreparedStatement findTagsByEntityStatement;
	private EntitySystem entitySystem;
	private EventSystem eventSystem;
	private UniqueType etEntityTag;

	public EntityTagSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity_tag(entity_tag_id INTEGER PRIMARY KEY, entity_id INTEGER, entity_tag_type INTEGER, tag_value INTEGER DEFAULT NULL)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity tag table", e);
			}
			getSchema().setVersionNumber(1);
		}

		eventSystem = game.getSystem(EventSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity_tag (entity_tag_id, entity_id, entity_tag_type, tag_value) VALUES (?, ?, ?, ?)");
			findByTagStatement = game.db().prepareStatement("SELECT entity_id FROM entity_tag WHERE entity_tag_type = ? AND entity_tag_id IN " + eventSystem.getValidEventsSubquery("entity_tag_id"));
			findTagsByEntityStatement = game.db().prepareStatement("SELECT entity_tag_id, entity_tag_type, tag_value FROM entity_tag WHERE entity_id = ? AND entity_tag_id IN " + eventSystem.getValidEventsSubquery("entity_tag_id"));
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare entity tag statements", e);
		}

		etEntityTag = game.getSystem(UniqueTypeSystem.class).getType("entity_tag");
	}

	public synchronized Reference add(Entity entity, UniqueType tagType, Long tagValue) throws DatabaseException {
		try {
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, entity.getId());
			addStatement.setLong(3, tagType.type());
			if (tagValue == null) {
				addStatement.setNull(4, java.sql.Types.INTEGER);
			} else {
				addStatement.setLong(4, tagValue);
			}
			addStatement.executeUpdate();
			// Create an event so the tag is visible at the current time
			eventSystem.addEventNow(etEntityTag, new Reference(newId, game));
			return new Reference(newId, game);
		} catch (SQLException e) {
			throw new DatabaseException("Unable to add entity tag", e);
		}
	}

	public synchronized Reference add(Entity entity, UniqueType tagType) throws DatabaseException {
		return add(entity, tagType, null);
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

	/**
	 * Check if an entity has a specific tag at a given time.
	 */
	public synchronized boolean hasTag(Entity entity, UniqueType tag, DTime when) {
		Set<Entity> tagged = findEntitiesByTag(tag, when);
		return tagged.contains(entity);
	}

	/**
	 * Add a tag to an entity (at current time) without a value.
	 */
	public synchronized Reference addTag(Entity entity, UniqueType tag) {
		return add(entity, tag, null);
	}

	/**
	 * Add a tag to an entity (at current time) with a numeric value.
	 */
	public synchronized Reference addTag(Entity entity, UniqueType tag, long value) {
		return add(entity, tag, value);
	}

	/**
	 * Get the value of a tag on an entity at a given time.
	 * Returns null if the tag doesn't exist or has no value.
	 */
	public synchronized Long getTagValue(Entity entity, UniqueType tag, DTime when) {
		try {
			findTagsByEntityStatement.setLong(1, entity.getId());
			eventSystem.setValidEventsSubqueryParameters(findTagsByEntityStatement, 2, etEntityTag, when);
			
			try (ResultSet rs = findTagsByEntityStatement.executeQuery()) {
				while (rs.next()) {
					long tagType = rs.getLong(2);
					
					if (tagType == tag.type()) {
						long value = rs.getLong(3);
						if (rs.wasNull()) {
							return null;
						}
						return value;
					}
				}
			}
			return null;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get tag value", e);
		}
	}

	/**
	 * Remove a tag from an entity by canceling the tag event at the specified time.
	 * Finds all tag events for this entity/tag combination and cancels them.
	 */
	public synchronized void removeTag(Entity entity, UniqueType tag, DTime when) {
		try {
			findTagsByEntityStatement.setLong(1, entity.getId());
			eventSystem.setValidEventsSubqueryParameters(findTagsByEntityStatement, 2, etEntityTag, when);
			
			try (ResultSet rs = findTagsByEntityStatement.executeQuery()) {
				while (rs.next()) {
					long tagId = rs.getLong(1);
					long tagType = rs.getLong(2);
					
					// If this tag matches the type we're removing
					if (tagType == tag.type()) {
						// Cancel the event
						eventSystem.cancelEvent(new Reference(tagId, game));
						log.log("Removed tag %s from entity %d", tag.toString(), entity.getId());
					}
				}
			}
		} catch (SQLException e) {
			throw new DatabaseException("Unable to remove tag", e);
		}
	}

	/**
	 * Get all tags for an entity at a given time.
	 */
	public synchronized Set<UniqueType> getTags(Entity entity, DTime when) {
		try {
			findTagsByEntityStatement.setLong(1, entity.getId());
			eventSystem.setValidEventsSubqueryParameters(findTagsByEntityStatement, 2, etEntityTag, when);
			
			Set<UniqueType> tags = new HashSet<>();
			UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
			try (ResultSet rs = findTagsByEntityStatement.executeQuery()) {
				while (rs.next()) {
					long tagType = rs.getLong(2);
					tags.add(new UniqueType(tagType, uts));
				}
			}
			return tags;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to get tags", e);
		}
	}
}
