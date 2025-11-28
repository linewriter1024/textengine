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
	
	// Command and message constants
	public static final String CMD_DROP = "drop";
	public static final String ERR_NOT_CARRYING = "not_carrying";
	public static final String ERR_NOWHERE = "nowhere";
	public static final String BROADCAST_DROPS = "actor_drops";
	// Note: EntitySystem.M_ACTOR_ID, EntitySystem.M_ACTOR_NAME defined in EntitySystem


	// Note: ItemSystem.M_ITEM_ID, ItemSystem.M_ITEM_NAME, ItemSystem.M_WEIGHT, ItemSystem.M_CARRY_WEIGHT defined in ItemSystem


	public DropItemAction(Game game, Actor actor, Entity item, DTime timeRequired) {
		super(game, actor, item, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_ITEM_DROP;
	}
	
	@Override
	public ActionValidation canExecute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		String itemName = getItemDescription();
		
		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty() || !itemContainers.get(0).getProvider().equals(actor)) {
			return ActionValidation.failure(
				CommandOutput.make(CMD_DROP)
					.error(ERR_NOT_CARRYING)
					.text(Markup.concat(
						Markup.raw("You aren't carrying "),
						Markup.em(itemName),
						Markup.raw(".")
					)));
		}
		
		// Check if actor has a location to drop into
		var actorContainers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (actorContainers.isEmpty()) {
			return ActionValidation.failure(
				CommandOutput.make(CMD_DROP)
					.error(ERR_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
		}
		
		return ActionValidation.success();
	}
	
	@Override
	public CommandOutput execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		
		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty() || !itemContainers.get(0).getProvider().equals(actor)) {
			return null; // Actor doesn't have this item
		}
		
		// Get current location
		var actorContainers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (actorContainers.isEmpty()) {
			return null; // Actor has no location
		}
		
		Entity currentLocation = actorContainers.get(0).getProvider();
		
		// Get descriptions
		String actorDesc = eds.getActorDescription(actor, ws.getCurrentTime());
		String itemDesc = getItemDescription();
		
		// Remove from actor
		var oldContainment = itemContainers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Add to current location
		rs.add(currentLocation, target, rs.rvContains);
		
		// Broadcast to all entities including the actor
		CommandOutput broadcast = CommandOutput.make(BROADCAST_DROPS)
			.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
			.put(EntitySystem.M_ACTOR_NAME, actorDesc)
			.put(ItemSystem.M_ITEM_ID, target.getKeyId())
			.put(ItemSystem.M_ITEM_NAME, itemDesc)
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" drops "),
				Markup.em(itemDesc),
				Markup.raw(".")
			));
		
		bs.broadcast(actor, broadcast);
		return broadcast;
	}
	
	@Override
	public String getDescription() {
		String itemDesc = getItemDescription();
		return "dropping " + itemDesc;
	}
	
	private String getEntityDescription(Entity entity) {
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		return eds.getSimpleDescription(entity, ws.getCurrentTime(), "something");
	}
	
	private String getItemDescription() {
		return getEntityDescription(target);
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
