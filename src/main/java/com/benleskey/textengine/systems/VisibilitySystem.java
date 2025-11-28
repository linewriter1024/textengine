package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * VisibilitySystem manages what entities can perceive based on spatial
 * relationships
 * and entity properties (prominence, visibility distance, etc.)
 */
public class VisibilitySystem extends SingletonGameSystem implements OnSystemInitialize {
	public UniqueType rvVisibleFrom;
	private RelationshipSystem relationshipSystem;
	private EntityTagSystem entityTagSystem;
	private WorldSystem worldSystem;

	// Tags for visibility control
	public UniqueType tagProminent; // Visible from far away (castles, towers)
	public UniqueType tagHidden; // Not visible unless very close (hidden items)
	public UniqueType tagObscured; // Partially hidden (in dense forest)

	public VisibilitySystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() {
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);

		UniqueTypeSystem uniqueTypeSystem = game.getSystem(UniqueTypeSystem.class);
		rvVisibleFrom = uniqueTypeSystem.getType("relationship_visible_from");
		tagProminent = uniqueTypeSystem.getType("tag_prominent");
		tagHidden = uniqueTypeSystem.getType("tag_hidden");
		tagObscured = uniqueTypeSystem.getType("tag_obscured");

		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No additional tables - uses relationships and tags
			getSchema().setVersionNumber(1);
		}
	}

	/**
	 * Mark one entity as visible from another.
	 * This creates an explicit visibility relationship (useful for distant
	 * landmarks).
	 * 
	 * @param from    The location from which the entity is visible
	 * @param visible The entity that can be seen
	 */
	public synchronized FullEvent<Relationship> makeVisibleFrom(Entity from, Entity visible) {
		// Check if visibility relationship already exists
		DTime now = worldSystem.getCurrentTime();
		List<RelationshipDescriptor> existing = relationshipSystem.getReceivingRelationships(from, rvVisibleFrom, now);

		for (RelationshipDescriptor rd : existing) {
			if (rd.getReceiver().getId() == visible.getId()) {
				// Relationship already exists, return null to indicate no change
				return null;
			}
		}

		// Create new visibility relationship
		return relationshipSystem.add(from, visible, rvVisibleFrom);
	}

	/**
	 * Get all entities visible to an observer at the current time.
	 * Visibility is determined by:
	 * 1. Entities in same container (nearby)
	 * 2. Entities in parent containers (surrounding area)
	 * 3. Prominent entities with explicit visibility relationships
	 * 4. Filtering out hidden entities
	 * 
	 * @param observer The entity doing the observing
	 * @return Descriptors for all visible entities, grouped by visibility level
	 */
	public synchronized List<VisibilityDescriptor> getVisibleEntities(Entity observer) {
		DTime when = worldSystem.getCurrentTime();
		List<VisibilityDescriptor> visible = new ArrayList<>();

		// 1. Get containers that contain the observer (current location + parents)
		Set<Entity> containers = relationshipSystem.getProvidingEntitiesRecursive(
				observer,
				relationshipSystem.rvContains,
				when);

		// 2. Get immediate siblings (things in same immediate container)
		Set<Entity> immediateSiblings = new HashSet<>();
		for (Entity container : containers) {
			List<RelationshipDescriptor> siblings = relationshipSystem.getReceivingRelationships(
					container,
					relationshipSystem.rvContains,
					when);
			immediateSiblings.addAll(siblings.stream()
					.map(RelationshipDescriptor::getReceiver)
					.filter(e -> !e.equals(observer)) // Don't see yourself
					.collect(Collectors.toSet()));
		}

		// Add immediate siblings as "nearby"
		for (Entity entity : immediateSiblings) {
			if (!isHidden(entity, when)) {
				visible.add(VisibilityDescriptor.builder()
						.entity(entity)
						.observer(observer)
						.distanceLevel(VisibilityLevel.NEARBY)
						.build());
			}
		}

		// 3. Get explicitly visible entities (distant landmarks)
		for (Entity container : containers) {
			List<RelationshipDescriptor> distantVisible = relationshipSystem.getReceivingRelationships(
					container,
					rvVisibleFrom,
					when);

			for (RelationshipDescriptor rd : distantVisible) {
				Entity entity = rd.getReceiver();
				if (!immediateSiblings.contains(entity) && !entity.equals(observer)) {
					// Only show if prominent or not obscured
					if (isProminent(entity, when) && !isObscured(entity, when)) {
						visible.add(VisibilityDescriptor.builder()
								.entity(entity)
								.observer(observer)
								.distanceLevel(VisibilityLevel.DISTANT)
								.build());
					}
				}
			}
		}

		return visible;
	}

	/**
	 * Check if an entity is marked as prominent (visible from far away).
	 */
	public synchronized boolean isProminent(Entity entity, DTime when) {
		return entityTagSystem.hasTag(entity, tagProminent, when);
	}

	/**
	 * Check if an entity is hidden (requires very close inspection).
	 */
	public synchronized boolean isHidden(Entity entity, DTime when) {
		return entityTagSystem.hasTag(entity, tagHidden, when);
	}

	/**
	 * Check if an entity is obscured (partially hidden).
	 */
	public synchronized boolean isObscured(Entity entity, DTime when) {
		return entityTagSystem.hasTag(entity, tagObscured, when);
	}

	/**
	 * Make an entity prominent (visible from distance).
	 */
	public synchronized void setProminent(Entity entity, boolean prominent) {
		if (prominent) {
			entityTagSystem.addTag(entity, tagProminent);
		} else {
			entityTagSystem.removeTag(entity, tagProminent, worldSystem.getCurrentTime());
		}
	}

	/**
	 * Visibility levels for entities.
	 */
	public enum VisibilityLevel {
		NEARBY, // In the same immediate container
		DISTANT // Visible from far away due to prominence
	}
}
