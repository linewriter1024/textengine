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
	 * For player avatars, filters the broadcast through AvatarBroadcastSystem.
	 * 
	 * @param output The broadcast output to relay
	 */
	@Override
	public void receiveBroadcast(CommandOutput output) {
		// Find the client controlling this actor
		for (Client client : game.getClients()) {
			if (client.getEntity().isPresent() && client.getEntity().get().getId() == this.getId()) {
				// Filter broadcast for player avatars
				CommandOutput filtered = game.getSystem(com.benleskey.textengine.systems.AvatarBroadcastSystem.class)
					.filterBroadcast(this, output);
				
				if (filtered != null) {
					client.sendOutput(filtered);
				}
				break;
			}
		}
	}
}
