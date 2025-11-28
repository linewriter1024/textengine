package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;

import java.util.List;

/**
 * Action for dropping an item in the actor's current location.
 * Broadcasts the action to nearby entities.
 */
public class DropItemAction extends Action {
	
	public DropItemAction(Game game, Actor actor, Entity item, DTime timeRequired) {
		super(game, actor, item, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_ITEM_DROP;
	}
	
	@Override
	public boolean execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		
		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty() || !itemContainers.get(0).getProvider().equals(actor)) {
			return false; // Actor doesn't have this item
		}
		
		// Get current location
		var actorContainers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (actorContainers.isEmpty()) {
			return false; // Actor has no location
		}
		
		Entity currentLocation = actorContainers.get(0).getProvider();
		
		// Get descriptions
		String actorDesc = getActorDescription();
		String itemDesc = getItemDescription();
		
		// Remove from actor
		var oldContainment = itemContainers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Add to current location
		rs.add(currentLocation, target, rs.rvContains);
		
		// Broadcast action
		bs.broadcast(actor, CommandOutput.make("actor_drops")
			.put("success", true)
			.put("actor_id", actor.getKeyId())
			.put("actor_name", actorDesc)
			.put("item_id", target.getKeyId())
			.put("item_name", itemDesc)
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" drops "),
				Markup.em(itemDesc),
				Markup.raw(".")
			)));
		
		return true;
	}
	
	@Override
	public String getDescription() {
		String itemDesc = getItemDescription();
		return "dropping " + itemDesc;
	}
	
	private String getActorDescription() {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(actor, ws.getCurrentTime());
		if (!looks.isEmpty()) {
			String desc = looks.get(0).getDescription();
			if (!desc.startsWith("a ") && !desc.startsWith("an ") && !desc.startsWith("the ")) {
				return "a " + desc;
			}
			return desc;
		}
		return "someone";
	}
	
	private String getItemDescription() {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(target, ws.getCurrentTime());
		return !looks.isEmpty() ? looks.get(0).getDescription() : "something";
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
