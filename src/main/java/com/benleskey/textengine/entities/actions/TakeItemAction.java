package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;

import java.util.List;

/**
 * Action for taking an item from the ground or a container.
 * Broadcasts the action to nearby entities.
 */
public class TakeItemAction extends Action {
	
	public TakeItemAction(Game game, Actor actor, Entity item, DTime timeRequired) {
		super(game, actor, item, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_ITEM_TAKE;
	}
	
	@Override
	public boolean execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		
		// Verify item exists and has a container
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return false; // Item has no container
		}
		
		Entity fromContainer = itemContainers.get(0).getProvider();
		
		// Get descriptions
		String actorDesc = getActorDescription();
		String itemDesc = getItemDescription();
		
		// Remove from old container
		var oldContainment = itemContainers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Add to actor's inventory
		rs.add(actor, target, rs.rvContains);
		
		// Build broadcast message
		// Check if taking from another container (not ground/location)
		boolean isFromContainer = fromContainer instanceof Item;
		
		CommandOutput broadcast;
		if (isFromContainer) {
			String containerDesc = getEntityDescription(fromContainer);
			broadcast = CommandOutput.make("actor_takes_from")
				.put("success", true)
				.put("actor_id", actor.getKeyId())
				.put("actor_name", actorDesc)
				.put("item_id", target.getKeyId())
				.put("item_name", itemDesc)
				.put("container_id", fromContainer.getKeyId())
				.put("container_name", containerDesc)
				.text(Markup.concat(
					Markup.escape(capitalize(actorDesc)),
					Markup.raw(" takes "),
					Markup.em(itemDesc),
					Markup.raw(" from "),
					Markup.em(containerDesc),
					Markup.raw(".")
				));
		} else {
			broadcast = CommandOutput.make("actor_takes")
				.put("success", true)
				.put("actor_id", actor.getKeyId())
				.put("actor_name", actorDesc)
				.put("item_id", target.getKeyId())
				.put("item_name", itemDesc)
				.text(Markup.concat(
					Markup.escape(capitalize(actorDesc)),
					Markup.raw(" takes "),
					Markup.em(itemDesc),
					Markup.raw(".")
				));
		}
		
		bs.broadcast(actor, broadcast);
		
		return true;
	}
	
	@Override
	public String getDescription() {
		String itemDesc = getItemDescription();
		return "taking " + itemDesc;
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
		return getEntityDescription(target);
	}
	
	private String getEntityDescription(Entity entity) {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(entity, ws.getCurrentTime());
		return !looks.isEmpty() ? looks.get(0).getDescription() : "something";
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
