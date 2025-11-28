package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.actions.MoveAction;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.util.Markup;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * System for filtering and customizing broadcast messages for player avatars.
 * Filters are registered by command ID and transform broadcasts before players see them.
 */
public class AvatarBroadcastSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	/**
	 * Filter function: (actor, broadcast) -> filtered broadcast (or null to suppress)
	 */
	private final Map<String, BiFunction<Actor, CommandOutput, CommandOutput>> filters = new HashMap<>();
	
	private EntitySystem entitySystem;
	private EntityDescriptionSystem entityDescriptionSystem;
	private WorldSystem worldSystem;
	
	public AvatarBroadcastSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		entitySystem = game.getSystem(EntitySystem.class);
		entityDescriptionSystem = game.getSystem(EntityDescriptionSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		
		// Register default filters
		registerDefaultFilters();
	}
	
	/**
	 * Register a filter for a specific command/broadcast type.
	 */
	public void registerFilter(String commandId, BiFunction<Actor, CommandOutput, CommandOutput> filter) {
		filters.put(commandId, filter);
		log.log("Registered avatar broadcast filter for command: %s", commandId);
	}
	
	/**
	 * Filter a broadcast for a player avatar.
	 * Returns the filtered broadcast, or null to suppress it.
	 */
	public CommandOutput filterBroadcast(Actor avatar, CommandOutput broadcast) {
		Object commandIdObj = broadcast.getO("output").orElse("");
		String commandId = commandIdObj != null ? commandIdObj.toString() : "";
		BiFunction<Actor, CommandOutput, CommandOutput> filter = filters.get(commandId);
		
		if (filter != null) {
			return filter.apply(avatar, broadcast);
		}
		
		// No filter - return broadcast unchanged
		return broadcast;
	}
	
	/**
	 * Register default filters for common broadcasts.
	 */
	private void registerDefaultFilters() {
		// Filter out player's own "leaves" messages
		registerFilter(MoveAction.BROADCAST_LEAVES, (avatar, broadcast) -> {
			Object actorIdObj = broadcast.getO(EntitySystem.M_ACTOR_ID).orElse(null);
			String actorId = actorIdObj != null ? actorIdObj.toString() : null;
			if (actorId != null && actorId.equals(avatar.getKeyId())) {
				// Suppress own leave message
				return null;
			}
			return broadcast;
		});
		
		// Transform player's own "arrives" into "You go to..."
		registerFilter(MoveAction.BROADCAST_ARRIVES, (avatar, broadcast) -> {
			Object actorIdObj = broadcast.getO(EntitySystem.M_ACTOR_ID).orElse(null);
			String actorId = actorIdObj != null ? actorIdObj.toString() : null;
			if (actorId != null && actorId.equals(avatar.getKeyId())) {
				// Transform to "You go to <destination>"
				Object destinationIdObj = broadcast.getO(RelationshipSystem.M_TO).orElse(null);
				String destinationId = destinationIdObj != null ? destinationIdObj.toString() : null;
				if (destinationId != null) {
					try {
						long destId = Long.parseLong(destinationId);
						Entity destination = entitySystem.get(destId);
						
						String destDesc = entityDescriptionSystem.getSimpleDescription(
							destination, 
							worldSystem.getCurrentTime(),
							"there"
						);
						
						return CommandOutput.make(MoveAction.BROADCAST_ARRIVES)
							.put(RelationshipSystem.M_TO, destinationId)
							.text(Markup.concat(
								Markup.raw("You go to "),
								Markup.em(destDesc),
								Markup.raw(".")
							));
					} catch (Exception e) {
						// Fall through to default
					}
				}
			}
			return broadcast;
		});
	}
}
