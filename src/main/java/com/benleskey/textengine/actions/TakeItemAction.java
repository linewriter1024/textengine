package com.benleskey.textengine.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;
import com.benleskey.textengine.util.StringUtils;

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
	// Note: EntitySystem.M_ENTITY_ID defined in EntitySystem

	// Note: RelationshipSystem.M_CONTAINER, RelationshipSystem.M_CONTAINER_ID,
	// RelationshipSystem.M_CONTAINER_NAME, RelationshipSystem.M_TARGET defined in
	// RelationshipSystem

	public static final String BROADCAST_TAKES = "actor_takes";
	public static final String BROADCAST_TAKES_FROM = "actor_takes_from";
	// Note: EntitySystem.M_ACTOR_ID, EntitySystem.M_ACTOR_NAME defined in
	// EntitySystem

	// Note: ItemSystem.M_ITEM_ID, ItemSystem.M_ITEM_NAME, ItemSystem.M_WEIGHT,
	// ItemSystem.M_CARRY_WEIGHT defined in ItemSystem

	public TakeItemAction(long id, Game game) {
		super(id, game);
	}

	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActionSystem.class).ACTION_ITEM_TAKE;
	}

	@Override
	public ActionValidation canExecute() {
		ItemSystem is = game.getSystem(ItemSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		Entity actor = (Entity) getActor().orElseThrow();
		Entity target = getTarget().orElseThrow();

		// Get item description for error messages
		String itemName = getEntityDescription(target);

		// Check if item still exists and has a container
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return ActionValidation.failure(
					CommandOutput.make(CMD_TAKE)
							.error(ERR_ITEM_NOT_FOUND)
							.text(Markup.concat(
									Markup.raw("You can't find "),
									Markup.em(itemName),
									Markup.raw("."))));
		}

		// Check if item is takeable
		if (!is.hasTag(target, is.TAG_TAKEABLE, ws.getCurrentTime())) {
			return ActionValidation.failure(
					CommandOutput.make(CMD_TAKE)
							.error(ERR_NOT_TAKEABLE)
							.text(Markup.concat(
									Markup.raw("You can't take "),
									Markup.em(itemName),
									Markup.raw("."))));
		}

		// Check weight constraints
		Long itemWeightGrams = is.getTagValue(target, is.TAG_WEIGHT, ws.getCurrentTime());
		Long carryWeightGrams = is.getTagValue(actor, is.TAG_CARRY_WEIGHT, ws.getCurrentTime());

		if (itemWeightGrams != null && carryWeightGrams != null) {
			com.benleskey.textengine.model.DWeight itemWeight = com.benleskey.textengine.model.DWeight
					.fromGrams(itemWeightGrams);
			com.benleskey.textengine.model.DWeight carryWeight = com.benleskey.textengine.model.DWeight
					.fromGrams(carryWeightGrams);

			if (itemWeight.isGreaterThan(carryWeight)) {
				return ActionValidation.failure(
						CommandOutput.make(CMD_TAKE)
								.error(ERR_TOO_HEAVY)
								.put(ItemSystem.M_WEIGHT, itemWeightGrams)
								.put(ItemSystem.M_CARRY_WEIGHT, carryWeightGrams)
								.text(Markup.concat(
										Markup.em(StringUtils.capitalize(itemName)),
										Markup.raw(" is too heavy to carry. It weighs "),
										Markup.escape(itemWeight.toString()),
										Markup.raw(", but you can only carry up to "),
										Markup.escape(carryWeight.toString()),
										Markup.raw("."))));
			}
		}

		return ActionValidation.success();
	}

	@Override
	public CommandOutput execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);

		Entity actor = (Entity) getActor().orElseThrow();
		Entity target = getTarget().orElseThrow();

		// Verify item exists and has a container
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return null; // Item has no container
		}

		Entity fromContainer = itemContainers.get(0).getProvider();

		// Get descriptions
		String actorDesc = eds.getDescriptionWithArticle(actor, ws.getCurrentTime(), "someone");
		String itemDesc = getEntityDescription(target);

		// Remove from old container by canceling its relationship event
		var oldContainment = itemContainers.get(0).getRelationship();
		EventSystem es = game.getSystem(EventSystem.class);
		es.cancelEventsByTypeAndReference(rs.etEntityRelationship, oldContainment, ws.getCurrentTime());

		// Add to actor's inventory
		rs.add(actor, target, rs.rvContains);

		// Build broadcast message
		// Check if taking from another container (not ground/location)
		boolean isFromContainer = fromContainer instanceof Item;

		CommandOutput broadcast;
		if (isFromContainer) {
			String containerDesc = getEntityDescription(fromContainer);
			broadcast = CommandOutput.make(BROADCAST_TAKES_FROM)
					.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
					.put(EntitySystem.M_ACTOR_NAME, actorDesc)
					.put(ItemSystem.M_ITEM_ID, target.getKeyId())
					.put(ItemSystem.M_ITEM_NAME, itemDesc)
					.put(RelationshipSystem.M_CONTAINER_ID, fromContainer.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerDesc)
					.text(Markup.concat(
							Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
							Markup.raw(" "),
							Markup.verb("take"),
							Markup.raw(" "),
							Markup.em(itemDesc),
							Markup.raw(" from "),
							Markup.em(containerDesc),
							Markup.raw(".")));
		} else {
			broadcast = CommandOutput.make(BROADCAST_TAKES)
					.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
					.put(EntitySystem.M_ACTOR_NAME, actorDesc)
					.put(ItemSystem.M_ITEM_ID, target.getKeyId())
					.put(ItemSystem.M_ITEM_NAME, itemDesc)
					.text(Markup.concat(
							Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
							Markup.raw(" "),
							Markup.verb("take"),
							Markup.raw(" "),
							Markup.em(itemDesc),
							Markup.raw(".")));
		}

		// Broadcast to all entities including the actor (player will see via broadcast)
		bs.broadcast(actor, broadcast);
		return broadcast;
	}

	@Override
	public String getDescription() {
		Entity target = getTarget().orElseThrow();
		String itemDesc = getEntityDescription(target);
		return "taking " + itemDesc;
	}

	private String getEntityDescription(Entity entity) {
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		return eds.getSimpleDescription(entity, ws.getCurrentTime(), "something");
	}

}
