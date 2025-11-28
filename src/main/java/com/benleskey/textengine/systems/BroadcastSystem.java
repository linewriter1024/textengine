package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.RelationshipDescriptor;

import java.util.List;

/**
 * System for broadcasting messages from one entity to nearby entities.
 * Handles finding entities in the same location and delivering broadcasts to
 * them.
 */
public class BroadcastSystem extends SingletonGameSystem implements OnSystemInitialize {

	private RelationshipSystem relationshipSystem;
	private WorldSystem worldSystem;

	public BroadcastSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);

		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - broadcasts are ephemeral
			getSchema().setVersionNumber(1);
		}
	}

	/**
	 * Broadcast a message from an entity to all entities in the same location,
	 * including the source.
	 * This ensures players and NPCs receive the same messages for their own
	 * actions.
	 * 
	 * @param source The entity broadcasting the message
	 * @param output The output to broadcast
	 */
	public void broadcast(Entity source, CommandOutput output) {
		DTime currentTime = worldSystem.getCurrentTime();

		// Find source's location
		var sourceContainers = relationshipSystem.getProvidingRelationships(source, relationshipSystem.rvContains,
				currentTime);
		if (sourceContainers.isEmpty()) {
			return; // Source is nowhere
		}

		Entity sourceLocation = sourceContainers.get(0).getProvider();

		// Get all entities in the same location
		List<Entity> entitiesInLocation = relationshipSystem
				.getReceivingRelationships(sourceLocation, relationshipSystem.rvContains, currentTime)
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.toList();

		// Broadcast to all entities including source (so players see their own actions)
		for (Entity entity : entitiesInLocation) {
			entity.receiveBroadcast(output);
		}
	}
}
