package com.benleskey.textengine.systems;

import java.util.HashMap;
import java.util.Map;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

/**
 * Generic system for registering and executing tag-based interactions between
 * items.
 * Allows plugins to define interactions like "TAG_CUT + TAG_CUTTABLE = cutting
 * behavior"
 * without hardcoding specific entity types.
 */
public class TagInteractionSystem extends SingletonGameSystem implements OnSystemInitialize {

	/**
	 * Functional interface for tag-based interaction handlers.
	 * Takes the actor, tool, target, and their display names, and returns a
	 * CommandOutput.
	 * 
	 * Note: Does not include Client - these interactions can be performed by NPCs
	 * too.
	 * The CommandOutput can be sent to relevant clients via broadcast or direct
	 * messaging.
	 */
	@FunctionalInterface
	public interface TagInteractionHandler {
		/**
		 * Handle the interaction between tool and target.
		 * 
		 * @param actor  The actor using the tool (could be player or NPC)
		 * @param tool   The tool entity (has toolTag) - may be the same as actor
		 *               for self-interactions
		 * @param target The target entity (has targetTag)
		 * @return Boolean indicating if the interaction was handled
		 */
		boolean handle(Entity actor, Entity tool,
				Entity target);
	}

	/**
	 * Key for storing tag interaction mappings.
	 * Combines toolTag and targetTag into a single lookup key.
	 */
	private record InteractionKey(UniqueType toolTag, UniqueType targetTag) {
	}

	private final Map<InteractionKey, TagInteractionHandler> interactions = new HashMap<>();

	public TagInteractionSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() {
		// No schema needed - this is a pure runtime system
	}

	/**
	 * Register a tag-based interaction.
	 * When a tool with toolTag is used on a target with targetTag, the handler will
	 * be called.
	 * 
	 * @param toolTag   The tag that the tool must have (e.g., TAG_CUT)
	 * @param targetTag The tag that the target must have (e.g., TAG_CUTTABLE)
	 * @param handler   The handler to call when this interaction occurs
	 */
	public void registerInteraction(UniqueType toolTag, UniqueType targetTag, TagInteractionHandler handler) {
		InteractionKey key = new InteractionKey(toolTag, targetTag);
		if (interactions.containsKey(key)) {
			throw new InternalException(
					String.format("Interaction between %s and %s already registered", toolTag, targetTag));
		}
		interactions.put(key, handler);
		log.log("Registered tag interaction: %s + %s", toolTag, targetTag);
	}

	/**
	 * Find and execute a registered interaction between tool and target.
	 * Checks all combinations of tags on both entities.
	 * 
	 * @param actor       The actor using the tool (could be player or NPC)
	 * @param tool        The tool entity
	 * @param target      The target entity
	 * @param currentTime The current game time
	 * @return Boolean indicating if an interaction was handled
	 */
	public boolean executeInteraction(Entity actor,
			Entity tool,
			Entity target,
			com.benleskey.textengine.model.DTime currentTime) {
		EntityTagSystem tagSystem = game.getSystem(EntityTagSystem.class);

		// Get all tags for both tool and target
		var toolTags = tagSystem.getTags(tool, currentTime);
		var targetTags = tagSystem.getTags(target, currentTime);

		// Check all combinations of tool tags × target tags
		for (UniqueType toolTag : toolTags) {
			for (UniqueType targetTag : targetTags) {
				InteractionKey key = new InteractionKey(toolTag, targetTag);
				TagInteractionHandler handler = interactions.get(key);

				if (handler != null) {
					return handler.handle(actor, tool, target);
				}
			}
		}

		return false;
	}

	/**
	 * Check if any interaction is registered for the given tool/target tag
	 * combination.
	 * 
	 * @param tool        The tool entity
	 * @param target      The target entity
	 * @param currentTime The current game time
	 * @return true if at least one interaction is registered
	 */
	public boolean hasInteraction(Entity tool, Entity target, com.benleskey.textengine.model.DTime currentTime) {
		EntityTagSystem tagSystem = game.getSystem(EntityTagSystem.class);

		var toolTags = tagSystem.getTags(tool, currentTime);
		var targetTags = tagSystem.getTags(target, currentTime);

		for (UniqueType toolTag : toolTags) {
			for (UniqueType targetTag : targetTags) {
				InteractionKey key = new InteractionKey(toolTag, targetTag);
				if (interactions.containsKey(key)) {
					return true;
				}
			}
		}

		return false;
	}
}
