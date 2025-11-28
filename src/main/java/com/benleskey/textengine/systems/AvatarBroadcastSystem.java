package com.benleskey.textengine.systems;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.actions.MoveAction;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.HashMap;
import java.util.Map;

/**
 * System for delivering broadcast messages to player avatars.
 * 
 * With the new markup system, broadcasts use <entity id="123">name</entity> and
 * <you>verb</you><notyou>verbs</notyou> tags that are processed on the client
 * side.
 * 
 * This system filters certain broadcasts for avatars to provide better UX:
 * - Hides "You leave" messages (redundant with command feedback)
 * - Converts "You arrive from X" to "You arrive at Y" (more natural)
 * 
 * The client's Markup.toTerminal() method handles converting entity references
 * to "you"
 * and selecting appropriate verb forms based on the avatar's entity ID.
 */
public class AvatarBroadcastSystem extends SingletonGameSystem {

	private interface BroadcastFilter {
		CommandOutput filter(Actor avatar, CommandOutput broadcast);
	}

	private final Map<String, BroadcastFilter> filters = new HashMap<>();

	public AvatarBroadcastSystem(Game game) {
		super(game);
		// Register default filters
		registerFilter(MoveAction.BROADCAST_LEAVES, (avatar, broadcast) -> {
			String actorId = broadcast.<String>getO(EntitySystem.M_ACTOR_ID).orElse(null);
			if (actorId != null && actorId.equals(avatar.getKeyId())) {
				return null;
			}
			return broadcast;
		});
		registerFilter(MoveAction.BROADCAST_ARRIVES, (avatar, broadcast) -> {
			String actorId = broadcast.<String>getO(EntitySystem.M_ACTOR_ID).orElse(null);
			if (actorId != null && actorId.equals(avatar.getKeyId())) {
				return createArrivalBroadcastForAvatar(avatar, broadcast);
			}
			return broadcast;
		});
	}

	public void registerFilter(String outputId, BroadcastFilter filter) {
		filters.put(outputId, filter);
	}

	/**
	 * Deliver a broadcast to a player avatar.
	 * Filters certain broadcasts to improve player experience.
	 */
	public void deliverBroadcast(Actor avatar, CommandOutput broadcast) {
		// Get command ID safely
		String commandId = broadcast.<String>getO(CommandOutput.M_OUTPUT_ID).orElse(null);

		// Apply registered filters
		if (commandId != null && filters.containsKey(commandId)) {
			CommandOutput filtered = filters.get(commandId).filter(avatar, broadcast);
			if (filtered == null) {
				return;
			}
			broadcast = filtered;
		}

		// Find the client controlling this actor
		for (Client client : game.getClients()) {
			if (client.getEntity().isPresent() && client.getEntity().get().getId() == avatar.getId()) {
				client.sendOutput(broadcast);
				break;
			}
		}
	}

	/**
	 * Create a modified arrival broadcast for the avatar.
	 * Changes "You arrive from X" to "You arrive at Y".
	 */
	private CommandOutput createArrivalBroadcastForAvatar(Actor avatar, CommandOutput originalBroadcast) {
		String toLocationId = originalBroadcast.<String>getO(RelationshipSystem.M_TO).orElse(null);
		if (toLocationId == null) {
			throw new IllegalStateException("No destination location in arrival broadcast");
		}

		EntitySystem es = game.getSystem(EntitySystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		long locationEntityId = Long.parseLong(toLocationId);
		String destDesc = eds.getSimpleDescription(es.get(locationEntityId), ws.getCurrentTime(), "somewhere");
		String actorDesc = eds.getActorDescription(avatar, ws.getCurrentTime());

		// Create new broadcast: "You arrive at <destination>"
		return CommandOutput.make(MoveAction.BROADCAST_ARRIVES)
				.put(EntitySystem.M_ACTOR_ID, avatar.getKeyId())
				.put(EntitySystem.M_ACTOR_NAME, actorDesc)
				.put(RelationshipSystem.M_TO, toLocationId)
				.text(Markup.concat(
						Markup.capital(Markup.entity(avatar.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("arrive", "arrives"),
						Markup.raw(" at "),
						Markup.em(destDesc),
						Markup.raw(".")));
	}
}
