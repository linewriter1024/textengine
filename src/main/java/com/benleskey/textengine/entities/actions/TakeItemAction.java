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
	
	// Command and message constants
	public static final String CMD_TAKE = "take";
	public static final String ERR_ITEM_NOT_FOUND = "item_not_found";
	public static final String ERR_NOT_TAKEABLE = "not_takeable";
	public static final String ERR_TOO_HEAVY = "too_heavy";
	public static final String M_ENTITY_ID = "entity_id";
	public static final String M_ITEM_NAME = "item_name";
	public static final String M_WEIGHT = "weight";
	public static final String M_CARRY_WEIGHT = "carry_weight";
	public static final String M_CONTAINER_ID = "container_id";
	public static final String M_CONTAINER_NAME = "container_name";
	public static final String BROADCAST_TAKES = "actor_takes";
	public static final String BROADCAST_TAKES_FROM = "actor_takes_from";
	public static final String M_ACTOR_ID = "actor_id";
	public static final String M_ACTOR_NAME = "actor_name";
	public static final String M_ITEM_ID = "item_id";
	
	public TakeItemAction(Game game, Actor actor, Entity item, DTime timeRequired) {
		super(game, actor, item, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_ITEM_TAKE;
	}
	
	@Override
	public ActionValidation canExecute() {
		ItemSystem is = game.getSystem(ItemSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Get item description for error messages
		String itemName = getItemDescription();
		
		// Check if item still exists and has a container
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return ActionValidation.failure(
				CommandOutput.make(CMD_TAKE)
					.error(ERR_ITEM_NOT_FOUND)
					.text(Markup.concat(
						Markup.raw("You can't find "),
						Markup.em(itemName),
						Markup.raw(".")
					)));
		}
		
		// Check if item is takeable
		if (!is.hasTag(target, is.TAG_TAKEABLE, ws.getCurrentTime())) {
			return ActionValidation.failure(
				CommandOutput.make(CMD_TAKE)
					.error(ERR_NOT_TAKEABLE)
					.text(Markup.concat(
						Markup.raw("You can't take "),
						Markup.em(itemName),
						Markup.raw(".")
					)));
		}
		
		// Check weight constraints
		Long itemWeightGrams = is.getTagValue(target, is.TAG_WEIGHT, ws.getCurrentTime());
		Long carryWeightGrams = is.getTagValue(actor, is.TAG_CARRY_WEIGHT, ws.getCurrentTime());
		
		if (itemWeightGrams != null && carryWeightGrams != null) {
			com.benleskey.textengine.model.DWeight itemWeight = com.benleskey.textengine.model.DWeight.fromGrams(itemWeightGrams);
			com.benleskey.textengine.model.DWeight carryWeight = com.benleskey.textengine.model.DWeight.fromGrams(carryWeightGrams);
			
			if (itemWeight.isGreaterThan(carryWeight)) {
				return ActionValidation.failure(
					CommandOutput.make(CMD_TAKE)
						.error(ERR_TOO_HEAVY)
						.put(M_WEIGHT, itemWeightGrams)
						.put(M_CARRY_WEIGHT, carryWeightGrams)
						.text(Markup.concat(
							Markup.em(capitalize(itemName)),
							Markup.raw(" is too heavy to carry. It weighs "),
							Markup.escape(itemWeight.toString()),
							Markup.raw(", but you can only carry up to "),
							Markup.escape(carryWeight.toString()),
							Markup.raw(".")
						)));
			}
		}
		
		return ActionValidation.success();
	}
	
	@Override
	public CommandOutput execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		ActorDescriptionSystem ads = game.getSystem(ActorDescriptionSystem.class);
		
		// Verify item exists and has a container
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return null; // Item has no container
		}
		
		Entity fromContainer = itemContainers.get(0).getProvider();
		
		// Get descriptions
		String actorDesc = ads.getActorDescription(actor, ws.getCurrentTime());
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
			broadcast = CommandOutput.make(BROADCAST_TAKES_FROM)
				.put(M_ACTOR_ID, actor.getKeyId())
				.put(M_ACTOR_NAME, actorDesc)
				.put(M_ITEM_ID, target.getKeyId())
				.put(M_ITEM_NAME, itemDesc)
				.put(M_CONTAINER_ID, fromContainer.getKeyId())
				.put(M_CONTAINER_NAME, containerDesc)
				.text(Markup.concat(
					Markup.escape(capitalize(actorDesc)),
					Markup.raw(" takes "),
					Markup.em(itemDesc),
					Markup.raw(" from "),
					Markup.em(containerDesc),
					Markup.raw(".")
				));
		} else {
			broadcast = CommandOutput.make(BROADCAST_TAKES)
				.put(M_ACTOR_ID, actor.getKeyId())
				.put(M_ACTOR_NAME, actorDesc)
				.put(M_ITEM_ID, target.getKeyId())
				.put(M_ITEM_NAME, itemDesc)
				.text(Markup.concat(
					Markup.escape(capitalize(actorDesc)),
					Markup.raw(" takes "),
					Markup.em(itemDesc),
					Markup.raw(".")
				));
		}
		
		// Broadcast to all entities including the actor (player will see via broadcast)
		bs.broadcast(actor, broadcast);
		return broadcast;
	}
	
	@Override
	public String getDescription() {
		String itemDesc = getItemDescription();
		return "taking " + itemDesc;
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
