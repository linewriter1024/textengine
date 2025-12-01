package com.benleskey.textengine.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;

/**
 * Action for moving an actor from one location to another.
 * Broadcasts departure and arrival messages to nearby entities.
 */
public class MoveAction extends Action {

	// Command and message constants
	public static final String CMD_GO = "go";
	public static final String ERR_NOWHERE = "nowhere";
	public static final String ERR_DESTINATION_NOT_FOUND = "destination_not_found";
	public static final String BROADCAST_LEAVES = "actor_leaves";
	public static final String BROADCAST_ARRIVES = "actor_arrives";
	// Note: M_ACTOR_ID, M_ACTOR_NAME defined in EntitySystem
	// Note: M_FROM, M_TO defined in RelationshipSystem

	public MoveAction(long id, Game game) {
		super(id, game);
	}

	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActionSystem.class).ACTION_MOVE;
	}

	@Override
	public ActionValidation canExecute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);

		Entity actor = (Entity) getActor().orElseThrow();
		Entity target = getTarget().orElseThrow();

		// Check if actor has a current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return ActionValidation.failure(
					CommandOutput.make(CMD_GO)
							.error(ERR_NOWHERE)
							.text(Markup.escape("You are nowhere.")));
		}

		// Check if destination still exists
		try {
			es.get(target.getId());
		} catch (Exception e) {
			return ActionValidation.failure(
					CommandOutput.make(CMD_GO)
							.error(ERR_DESTINATION_NOT_FOUND)
							.text(Markup.escape("That destination doesn't exist.")));
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

		// Get current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return null; // Actor has no location
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Get actor description
		String actorDesc = eds.getDescriptionWithArticle(actor, ws.getCurrentTime(), "someone");

		// Broadcast departure to entities in current location
		// Using new markup: <capital><entity id="X">name</entity></capital>
		// <you>leave</you><notyou>leaves</notyou>
		bs.broadcast(actor, CommandOutput.make(BROADCAST_LEAVES)
				.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
				.put(EntitySystem.M_ACTOR_NAME, actorDesc)
				.put(RelationshipSystem.M_FROM, currentLocation.getKeyId())
				.text(Markup.concat(
						Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("leave", "leaves"),
						Markup.raw("."))));

		// Cancel old containment relationship by canceling its event
		var oldContainment = containers.get(0).getRelationship();
		EventSystem es = game.getSystem(EventSystem.class);
		es.cancelEventsByTypeAndReference(rs.etEntityRelationship, oldContainment, ws.getCurrentTime());

		// Create new containment relationship
		rs.add(target, actor, rs.rvContains);

		// Get destination description
		String destDesc = eds.getSimpleDescription(target, ws.getCurrentTime(), "somewhere");

		// Broadcast arrival to entities in destination
		// Using new markup: <capital><entity id="X">name</entity></capital>
		// <you>arrive</you><notyou>arrives</notyou>
		CommandOutput arrivalBroadcast = CommandOutput.make(BROADCAST_ARRIVES)
				.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
				.put(EntitySystem.M_ACTOR_NAME, actorDesc)
				.put(RelationshipSystem.M_TO, target.getKeyId())
				.text(Markup.concat(
						Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("arrive", "arrives"),
						Markup.raw(" from "),
						Markup.em(destDesc),
						Markup.raw(".")));

		bs.broadcast(actor, arrivalBroadcast);
		return arrivalBroadcast;
	}

	@Override
	public String getDescription() {
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		Entity target = getTarget().orElseThrow();
		String destDesc = eds.getSimpleDescription(target, ws.getCurrentTime(), "somewhere");

		return "moving to " + destDesc;
	}
}
