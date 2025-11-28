package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Generic system for registering and executing tag-based interactions between items.
 * Allows plugins to define interactions like "TAG_CUT + TAG_CUTTABLE = cutting behavior"
 * without hardcoding specific entity types.
 */
public class TagInteractionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	/**
	 * Functional interface for tag-based interaction handlers.
	 * Takes the actor, tool, target, and their display names, and returns a CommandOutput.
	 * 
	 * Note: Does not include Client - these interactions can be performed by NPCs too.
	 * The CommandOutput can be sent to relevant clients via broadcast or direct messaging.
	 */
	@FunctionalInterface
	public interface TagInteractionHandler {
		/**
		 * Handle the interaction between tool and target.
		 * 
		 * @param actor The actor using the tool (could be player or NPC)
		 * @param tool The tool entity (has toolTag) - may be the same as actor for self-interactions
		 * @param toolName Human-readable name of the tool
		 * @param target The target entity (has targetTag)
		 * @param targetName Human-readable name of the target
		 * @return CommandOutput describing what happened, or null if interaction should fall through
		 */
		CommandOutput handle(Entity actor, Entity tool, String toolName, 
		                     Entity target, String targetName);
	}
	
	/**
	 * Key for storing tag interaction mappings.
	 * Combines toolTag and targetTag into a single lookup key.
	 */
	private record InteractionKey(UniqueType toolTag, UniqueType targetTag) {}
	
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
	 * When a tool with toolTag is used on a target with targetTag, the handler will be called.
	 * 
	 * @param toolTag The tag that the tool must have (e.g., TAG_CUT)
	 * @param targetTag The tag that the target must have (e.g., TAG_CUTTABLE)
	 * @param handler The handler to call when this interaction occurs
	 */
	public void registerInteraction(UniqueType toolTag, UniqueType targetTag, TagInteractionHandler handler) {
		InteractionKey key = new InteractionKey(toolTag, targetTag);
		if (interactions.containsKey(key)) {
			log.log("WARNING: Overwriting existing interaction for %s + %s", toolTag, targetTag);
		}
		interactions.put(key, handler);
		log.log("Registered tag interaction: %s + %s", toolTag, targetTag);
	}
	
	/**
	 * Find and execute a registered interaction between tool and target.
	 * Checks all combinations of tags on both entities.
	 * 
	 * @param actor The actor using the tool (could be player or NPC)
	 * @param tool The tool entity
	 * @param toolName Human-readable name of the tool
	 * @param target The target entity
	 * @param targetName Human-readable name of the target
	 * @param currentTime The current game time
	 * @return Optional containing CommandOutput if an interaction was found and executed, empty otherwise
	 */
	public Optional<CommandOutput> executeInteraction(Entity actor, 
	                                                   Entity tool, String toolName,
	                                                   Entity target, String targetName,
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
					// Found a matching interaction!
					CommandOutput output = handler.handle(actor, tool, toolName, target, targetName);
					if (output != null) {
						return Optional.of(output);
					}
				}
			}
		}
		
		return Optional.empty();
	}
	
	/**
	 * Execute an interaction. The interaction handler is responsible for broadcasting
	 * to all observers using BroadcastSystem if needed.
	 * 
	 * This method now simply executes the interaction - the handler itself (e.g., Tree.onCut)
	 * uses BroadcastSystem to notify all nearby entities with proper markup.
	 * 
	 * @param actor The actor performing the interaction
	 * @param actorName Human-readable name of the actor (unused now, kept for compatibility)
	 * @param tool The tool being used
	 * @param toolName Human-readable name of the tool
	 * @param target The target of the interaction
	 * @param targetName Human-readable name of the target
	 * @param currentTime The current game time
	 * @return Optional containing CommandOutput for the actor, empty if no interaction found
	 */
	public Optional<CommandOutput> executeInteractionWithBroadcast(
			Entity actor, String actorName,
			Entity tool, String toolName,
			Entity target, String targetName,
			com.benleskey.textengine.model.DTime currentTime) {
		
		// Simply execute the interaction - the handler uses BroadcastSystem internally
		return executeInteraction(actor, tool, toolName, target, targetName, currentTime);
	}
	
	/**
	 * Check if any interaction is registered for the given tool/target tag combination.
	 * 
	 * @param tool The tool entity
	 * @param target The target entity
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
