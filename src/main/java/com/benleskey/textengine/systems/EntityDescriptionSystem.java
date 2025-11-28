package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.UniqueType;

import java.util.*;

/**
 * Unified system for generating consistent entity descriptions.
 * Provides descriptions for all entity types based on their characteristics:
 * - Player actors: "Player <id>"
 * - NPC actors: look description with article (e.g., "a goblin")
 * - Other entities: look description or fallback
 * - Tag-based supplementary descriptions (e.g., "This resource is abundant here.")
 */
public class EntityDescriptionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Map from UniqueType tag to list of supplementary descriptions
	private final Map<UniqueType, List<String>> tagDescriptions = new HashMap<>();
	
	private EntitySystem entitySystem;
	private EntityTagSystem entityTagSystem;
	private LookSystem lookSystem;
	
	public EntityDescriptionSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		entitySystem = game.getSystem(EntitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		lookSystem = game.getSystem(LookSystem.class);
	}
	
	// ===== Tag-based Supplementary Descriptions =====
	
	/**
	 * Register a supplementary description for a tag.
	 * Multiple descriptions can be registered for the same tag.
	 * These provide additional context (e.g., "This resource is abundant here.").
	 */
	public void registerTagDescription(UniqueType tag, String description) {
		tagDescriptions.computeIfAbsent(tag, k -> new ArrayList<>()).add(description);
		log.log("Registered tag description for: " + tag);
	}
	
	/**
	 * Get all tag-based supplementary descriptions for an entity.
	 * Returns descriptions based on the entity's tags at the given time.
	 */
	public List<String> getTagDescriptions(Entity entity, DTime when) {
		List<String> descriptions = new ArrayList<>();
		
		// Get all tags for the entity
		Set<UniqueType> entityTags = entityTagSystem.getTags(entity, when);
		
		// Collect descriptions for each tag
		for (UniqueType tag : entityTags) {
			List<String> tagDescs = tagDescriptions.get(tag);
			if (tagDescs != null) {
				descriptions.addAll(tagDescs);
			}
		}
		
		return descriptions;
	}
	
	/**
	 * Get tag-based descriptions for specific tags only.
	 * Useful when you want descriptions for specific tags rather than all tags.
	 */
	public List<String> getTagDescriptionsFor(Entity entity, DTime when, UniqueType... tags) {
		List<String> descriptions = new ArrayList<>();
		
		for (UniqueType tag : tags) {
			if (entityTagSystem.hasTag(entity, tag, when)) {
				List<String> tagDescs = tagDescriptions.get(tag);
				if (tagDescs != null) {
					descriptions.addAll(tagDescs);
				}
			}
		}
		
		return descriptions;
	}
	
	/**
	 * Check if a tag has any descriptions registered.
	 */
	public boolean hasDescriptionsFor(UniqueType tag) {
		return tagDescriptions.containsKey(tag) && !tagDescriptions.get(tag).isEmpty();
	}
	
	// ===== Primary Entity Descriptions =====
	
	/**
	 * Get a description of any entity.
	 * - For player actors: "Player <id>"
	 * - For NPC actors: look description with article
	 * - For other entities: look description
	 */
	public String getDescription(Entity entity, DTime currentTime) {
		// Special handling for actors
		if (entity instanceof Actor) {
			return getActorDescription((Actor) entity, currentTime);
		}
		
		// For non-actors, use look description
		return getSimpleDescription(entity, currentTime);
	}
	
	/**
	 * Get a description of an actor suitable for broadcast messages.
	 * Players: "Player <id>"
	 * NPCs: look description with article (e.g., "a goblin")
	 */
	public String getActorDescription(Actor actor, DTime currentTime) {
		// Check if this is a player (has TAG_AVATAR)
		boolean isPlayer = entityTagSystem.hasTag(actor, entitySystem.TAG_AVATAR, currentTime);
		
		if (isPlayer) {
			return "Player " + actor.getId();
		} else {
			// NPC - use look description with article
			return getDescriptionWithArticle(actor, currentTime, "someone");
		}
	}
	
	/**
	 * Get a simple description from look system.
	 * Returns the look description or fallback if none exists.
	 */
	public String getSimpleDescription(Entity entity, DTime currentTime) {
		return getSimpleDescription(entity, currentTime, "something");
	}
	
	/**
	 * Get a simple description from look system with custom fallback.
	 */
	public String getSimpleDescription(Entity entity, DTime currentTime, String fallback) {
		List<LookDescriptor> looks = lookSystem.getLooksFromEntity(entity, currentTime);
		return !looks.isEmpty() ? looks.get(0).getDescription() : fallback;
	}
	
	/**
	 * Get a description with article prepended if not already present.
	 * Useful for NPCs and items in narrative text.
	 */
	public String getDescriptionWithArticle(Entity entity, DTime currentTime, String fallback) {
		List<LookDescriptor> looks = lookSystem.getLooksFromEntity(entity, currentTime);
		if (!looks.isEmpty()) {
			String desc = looks.get(0).getDescription();
			// Add article if not already present
			if (!desc.startsWith("a ") && !desc.startsWith("an ") && !desc.startsWith("the ")) {
				return "a " + desc;
			}
			return desc;
		}
		return fallback;
	}
}
