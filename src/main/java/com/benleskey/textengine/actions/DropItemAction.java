package com.benleskey.textengine.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionResult;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.EventSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

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
	// Note: EntitySystem.M_ACTOR_ID, EntitySystem.M_ACTOR_NAME defined in
	// EntitySystem

	// Note: ItemSystem.M_ITEM_ID, ItemSystem.M_ITEM_NAME, ItemSystem.M_WEIGHT,
	// ItemSystem.M_CARRY_WEIGHT defined in ItemSystem

	public DropItemAction(long id, Game game) {
		super(id, game);
	}

	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActionSystem.class).ACTION_ITEM_DROP;
	}

	@Override
	public ActionValidation canExecute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		Entity actor = (Entity) getActor().orElseThrow();
		Entity target = getTarget().orElseThrow();

		String itemName = getEntityDescription(target);

		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty() || !itemContainers.get(0).getProvider().equals(actor)) {
			return ActionValidation.failure(
					CommandOutput.make(CMD_DROP)
							.error(ERR_NOT_CARRYING)
							.text(Markup.concat(
									Markup.raw("You aren't carrying "),
									Markup.em(itemName),
									Markup.raw("."))));
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
	public ActionResult execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);

		Entity actor = (Entity) getActor().orElseThrow();
		Entity target = getTarget().orElseThrow();

		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty() || !itemContainers.get(0).getProvider().equals(actor)) {
			return ActionResult.failure(); // Actor doesn't have this item
		}

		// Get current location
		var actorContainers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (actorContainers.isEmpty()) {
			return ActionResult.failure(); // Actor has no location
		}

		Entity currentLocation = actorContainers.get(0).getProvider();

		// Get descriptions
		String actorDesc = eds.getDescriptionWithArticle(actor, ws.getCurrentTime(), "someone");
		String itemDesc = getEntityDescription(target);

		// Remove from actor by canceling its relationship event
		var oldContainment = itemContainers.get(0).getRelationship();
		EventSystem es = game.getSystem(EventSystem.class);
		es.cancelEventsByTypeAndReference(rs.etEntityRelationship, oldContainment, ws.getCurrentTime());

		// Add to current location
		rs.add(currentLocation, target, rs.rvContains);

		// Broadcast to all entities including the actor
		CommandOutput broadcast = CommandOutput.make(BROADCAST_DROPS)
				.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
				.put(EntitySystem.M_ACTOR_NAME, actorDesc)
				.put(ItemSystem.M_ITEM_ID, target.getKeyId())
				.put(ItemSystem.M_ITEM_NAME, itemDesc)
				.text(Markup.concat(
						Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("drop"),
						Markup.raw(" "),
						Markup.em(itemDesc),
						Markup.raw(".")));

		bs.broadcast(actor, broadcast);
		return ActionResult.success();
	}

	@Override
	public String getDescription() {
		Entity target = getTarget().orElseThrow();
		String itemDesc = getEntityDescription(target);
		return "dropping " + itemDesc;
	}

	private String getEntityDescription(Entity entity) {
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		return eds.getSimpleDescription(entity, ws.getCurrentTime(), "something");
	}
}
