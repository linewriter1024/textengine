package com.benleskey.textengine.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Entity;

public class Actor extends Entity {

	public Actor(long id, Game game) {
		super(id, game);
	}

	/**
	 * Receive a broadcast event from another entity.
	 * Relays the broadcast to this actor's client, if one exists.
	 * With the new markup system, broadcasts use <entity> and <you>/<notyou> tags
	 * that are processed client-side, so no filtering is needed.
	 * 
	 * @param output The broadcast output to relay
	 */
	@Override
	public void receiveBroadcast(CommandOutput output) {
		// Find the client controlling this actor
		for (Client client : game.getClients()) {
			if (client.getEntity().isPresent() && client.getEntity().get().getId() == this.getId()) {
				// Send broadcast directly - client handles markup conversion
				game.getSystem(com.benleskey.textengine.systems.AvatarBroadcastSystem.class)
						.deliverBroadcast(this, output);
				break;
			}
		}
	}
}
