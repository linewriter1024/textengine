package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSkeletonInteraction;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class EntitySystem extends SingletonGameSystem implements OnSystemInitialize {
	private PreparedStatement addStatement;
	private PreparedStatement getStatement;
	private final Map<UniqueType, Class<? extends Entity>> entityTypes = new HashMap<>();
	private EntityTagSystem tagSystem;
	private WorldSystem worldSystem;
	private UniqueTypeSystem typeSystem;

	// LRU cache for entity instances
	private final Map<Long, Entity> entityCache = new LinkedHashMap<Long, Entity>(Game.CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Entity> eldest) {
			return size() > Game.CACHE_SIZE;
		}
	};

	// Common message field constants for entity-related data
	public static final String M_ENTITY_ID = "entity_id";
	public static final String M_ACTOR_ID = "actor_id";
	public static final String M_ACTOR_NAME = "actor_name";

	// Common entity tags (initialized in onSystemInitialize)
	public UniqueType TAG_ENTITY_CREATED; // Time when entity was created (in milliseconds)
	public UniqueType TAG_AVATAR; // Entity is controlled by a player/client
	public UniqueType TAG_SKELETON; // Entity is a skeleton that needs to be populated before interaction

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
		TAG_AVATAR = typeSystem.getType("entity_tag_avatar");
		TAG_SKELETON = typeSystem.getType("entity_tag_skeleton");
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
			if (!entityTypes.get(type).isInstance(dummy)) {
				throw new InternalException("Attempted to create entity with incorrect class. Got " + clazz
						+ " but the registered class for " + type + " is " + entityTypes.get(type));
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
		return Optional.ofNullable(entityTypes.get(type))
				.orElseThrow(() -> new InternalException("Could not fetch entity type: " + type));
	}

	public synchronized <T extends Entity> T get(long id, Class<T> clazz) {
		// Check cache first
		Entity cached = entityCache.get(id);
		if (cached != null && clazz.isInstance(cached)) {
			return clazz.cast(cached);
		}

		// Create new instance
		try {
			T entity = clazz.getDeclaredConstructor(long.class, Game.class).newInstance(id, game);
			// Cache the entity (only if id != 0, which is used for dummy entities)
			if (id != 0) {
				entityCache.put(id, entity);
			}
			return entity;
		} catch (InvocationTargetException | InstantiationException | IllegalAccessException
				| NoSuchMethodException e) {
			throw new InternalException("Unable to add entity of class " + clazz.toGenericString(), e);
		}
	}

	public synchronized Entity get(long id) throws DatabaseException {
		// Check cache first
		Entity cached = entityCache.get(id);
		if (cached != null) {
			return cached;
		}

		// Fetch from database
		try {
			getStatement.setLong(1, id);
			try (ResultSet rs = getStatement.executeQuery()) {
				if (rs.next()) {
					return get(id, getEntityClass(game.getUniqueTypeSystem().getTypeFromRaw(rs.getLong(1))));
				}
			}
			throw new InternalException("Attempted to fetch entity that did not exist: " + id);
		} catch (SQLException e) {
			throw new DatabaseException("Could not get entity " + id, e);
		}
	}

	/**
	 * Check if any entities of the given type exist.
	 * 
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
	public com.benleskey.textengine.model.Reference updateTagValue(Entity entity, UniqueType tag, long newValue,
			DTime when) {
		return tagSystem.updateTagValue(entity, tag, newValue, when);
	}

	/**
	 * Check if an entity is a skeleton (placeholder that needs population).
	 */
	public boolean isSkeleton(Entity entity) {
		return tagSystem.hasTag(entity, TAG_SKELETON, worldSystem.getCurrentTime());
	}

	/**
	 * Mark an entity as a skeleton (placeholder that needs population before
	 * interaction).
	 */
	public void markAsSkeleton(Entity entity) {
		tagSystem.addTag(entity, TAG_SKELETON);
	}

	/**
	 * Ensure an entity is fully populated before interaction.
	 * If the entity has TAG_SKELETON, fires OnSkeletonInteraction hook and removes
	 * the tag.
	 * This should be called before any significant interaction with an entity
	 * (entering a place, examining an object, etc.).
	 * 
	 * @param entity The entity to ensure is populated
	 */
	public synchronized void ensurePopulated(Entity entity) {
		if (!isSkeleton(entity)) {
			return; // Already populated
		}

		log.log("Populating skeleton entity %d", entity.getId());

		// Remove the skeleton tag
		tagSystem.removeTag(entity, TAG_SKELETON, worldSystem.getCurrentTime());

		// Fire the hook to let plugins populate the entity
		game.doHookEvent(OnSkeletonInteraction.class, handler -> handler.onSkeletonInteraction(entity));
	}
}
