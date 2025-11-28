package com.benleskey.textengine.systems;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;

/**
 * System for delivering broadcast messages to player avatars.
 * 
 * With the new markup system, broadcasts use <entity id="123">name</entity> and
 * <you>verb</you><notyou>verbs</notyou> tags that are processed on the client side.
 * This system simply delivers broadcasts to avatars without filtering.
 * 
 * The client's Markup.toTerminal() method handles converting entity references to "you"
 * and selecting appropriate verb forms based on the avatar's entity ID.
 */
public class AvatarBroadcastSystem extends SingletonGameSystem {
	
	public AvatarBroadcastSystem(Game game) {
		super(game);
	}
	
	/**
	 * Deliver a broadcast to a player avatar.
	 * No filtering is needed - the markup system handles you/notyou conversion.
	 */
	public void deliverBroadcast(Actor avatar, CommandOutput broadcast) {
		// Find the client controlling this actor
		for (Client client : game.getClients()) {
			if (client.getEntity().isPresent() && client.getEntity().get().getId() == avatar.getId()) {
				client.sendOutput(broadcast);
				break;
			}
		}
	}
}
