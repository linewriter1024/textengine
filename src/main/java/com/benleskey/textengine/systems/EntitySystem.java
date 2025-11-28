package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntitySystem extends SingletonGameSystem implements OnSystemInitialize {
	private PreparedStatement addStatement;
	private PreparedStatement getStatement;
	private final Map<UniqueType, Class<? extends Entity>> entityTypes = new HashMap<>();
	private EntityTagSystem tagSystem;
	private WorldSystem worldSystem;
	private UniqueTypeSystem typeSystem;
	
	// Common entity tags (initialized in onSystemInitialize)
	public UniqueType TAG_ENTITY_CREATED;  // Time when entity was created (in milliseconds)
	public UniqueType TAG_TICKABLE;  // Entity receives periodic tick updates
	public UniqueType TAG_LAST_TICK;  // Last time entity was ticked (in milliseconds)
	public UniqueType TAG_ACTOR;  // Entity can perform actions (player, NPC)
	public UniqueType TAG_AVATAR;  // Entity is controlled by a player/client

	public EntitySystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		int v = getSchema().getVersionNumber();

		if (v == 0) {
			try {
				try (Statement s = game.db().createStatement()) {
					s.executeUpdate("CREATE TABLE entity(entity_id INTEGER PRIMARY KEY, type INTEGER)");
				}
			} catch (SQLException e) {
				throw new DatabaseException("Unable to create entity table", e);
			}
			getSchema().setVersionNumber(1);
		}

		try {
			addStatement = game.db().prepareStatement("INSERT INTO entity (entity_id, type) VALUES (?, ?)");
			getStatement = game.db().prepareStatement("SELECT type FROM entity WHERE entity_id = ?");
		} catch (SQLException e) {
			throw new DatabaseException("Unable to prepare entity statements", e);
		}
		
		// Get systems for storing creation time
		tagSystem = game.getSystem(EntityTagSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		typeSystem = game.getSystem(UniqueTypeSystem.class);
		
		// Initialize entity tags
		TAG_ENTITY_CREATED = typeSystem.getType("entity_tag_entity_created");
		TAG_TICKABLE = typeSystem.getType("entity_tag_tickable");
		TAG_LAST_TICK = typeSystem.getType("entity_tag_last_tick");
		TAG_ACTOR = typeSystem.getType("entity_tag_actor");
		TAG_AVATAR = typeSystem.getType("entity_tag_avatar");
	}

	@SuppressWarnings("null") // Generic type T will never be null
	public synchronized <T extends Entity> void registerEntityType(Class<T> clazz) {
		T dummy = get(0, clazz);
		UniqueType type = dummy.getEntityType();
		this.entityTypes.put(type, clazz);
		log.log("Registered entity type %s to class %s", type, clazz.getCanonicalName());
	}

	@SuppressWarnings("null") // Generic type T will never be null
	public synchronized <T extends Entity> T add(Class<T> clazz) throws DatabaseException {
		try {
			T dummy = get(0, clazz);
			UniqueType type = dummy.getEntityType();
			if(!entityTypes.get(type).isInstance(dummy)) {
				throw new InternalException("Attempted to create entity with incorrect class. Got " + clazz + " but the registered class for " + type + " is " + entityTypes.get(type));
			}
			long newId = game.getNewGlobalId();
			addStatement.setLong(1, newId);
			addStatement.setLong(2, type.type());
			addStatement.executeUpdate();
			
			// Store entity creation time
			T entity = get(newId, clazz);
			DTime creationTime = worldSystem.getCurrentTime();
			tagSystem.addTag(entity, TAG_ENTITY_CREATED, creationTime.toMilliseconds());
			
			return entity;
		} catch (SQLException e) {
			throw new DatabaseException("Unable to add entity", e);
		}
	}

	public synchronized Class<? extends Entity> getEntityClass(UniqueType type) {
		return Optional.ofNullable(entityTypes.get(type)).orElseThrow(() -> new InternalException("Could not fetch entity type: " + type));
	}

	public synchronized <T extends Entity> T get(long id, Class<T> clazz) {
		try {
			return clazz.getDeclaredConstructor(long.class, Game.class).newInstance(id, game);
		}
		catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
			throw new InternalException("Unable to add entity of class " + clazz.toGenericString(), e);
		}
	}

	public synchronized Entity get(long id) throws DatabaseException {
		try {
			getStatement.setLong(1, id);
			try(ResultSet rs = getStatement.executeQuery()) {
				if(rs.next()) {
					return get(id, getEntityClass(game.getUniqueTypeSystem().getTypeFromRaw(rs.getLong(1))));
				}
			}
			throw new InternalException("Attempted to fetch entity that did not exist: " + id);
		} catch(SQLException e) {
			throw new DatabaseException("Could not get entity " + id, e);
		}
	}
	
	/**
	 * Check if any entities of the given type exist.
	 * @param entityType The entity type to check for
	 * @return true if at least one entity of this type exists
	 */
	public synchronized boolean hasEntitiesOfType(UniqueType entityType) throws DatabaseException {
		try {
			var stmt = game.db().prepareStatement("SELECT COUNT(*) FROM entity WHERE type = ?");
			stmt.setLong(1, entityType.type());
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) > 0;
				}
			}
			return false;
		} catch (SQLException e) {
			throw new DatabaseException("Could not check for entities of type " + entityType, e);
		}
	}
	
	/**
	 * Get the numeric value of a tag on an entity.
	 */
	public Long getTagValue(Entity entity, UniqueType tag, DTime when) {
		return tagSystem.getTagValue(entity, tag, when);
	}
	
	/**
	 * Add a tag to an entity.
	 */
	public com.benleskey.textengine.model.Reference addTag(Entity entity, UniqueType tag) {
		return tagSystem.addTag(entity, tag);
	}
	
	/**
	 * Add a tag with a numeric value to an entity.
	 */
	public com.benleskey.textengine.model.Reference addTag(Entity entity, UniqueType tag, long value) {
		return tagSystem.addTag(entity, tag, value);
	}
	
	/**
	 * Update a tag value (cancels old value and adds new one).
	 */
	public com.benleskey.textengine.model.Reference updateTagValue(Entity entity, UniqueType tag, long newValue, DTime when) {
		return tagSystem.updateTagValue(entity, tag, newValue, when);
	}
}
