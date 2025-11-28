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
	public static final String M_ACTOR_ID = "actor_id";
	public static final String M_ACTOR_NAME = "actor_name";
	public static final String M_FROM = "from";
	public static final String M_TO = "to";
	
	public MoveAction(Game game, Actor actor, Entity destination, DTime timeRequired) {
		super(game, actor, destination, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_MOVE;
	}
	
	@Override
	public ActionValidation canExecute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);
		
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
		ActorDescriptionSystem ads = game.getSystem(ActorDescriptionSystem.class);
		
		// Get current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return null; // Actor has no location
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get actor description
		String actorDesc = ads.getActorDescription(actor, ws.getCurrentTime());
		
		// Broadcast departure to entities in current location
		bs.broadcast(actor, CommandOutput.make(BROADCAST_LEAVES)
			.put(M_ACTOR_ID, actor.getKeyId())
			.put(M_ACTOR_NAME, actorDesc)
			.put(M_FROM, currentLocation.getKeyId())
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" leaves.")
			)));
		
		// Cancel old containment relationship
		var oldContainment = containers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Create new containment relationship
		rs.add(target, actor, rs.rvContains);
		
		// Broadcast arrival to entities in destination
		CommandOutput arrivalBroadcast = CommandOutput.make(BROADCAST_ARRIVES)
			.put(M_ACTOR_ID, actor.getKeyId())
			.put(M_ACTOR_NAME, actorDesc)
			.put(M_TO, target.getKeyId())
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" arrives.")
			));
		
		bs.broadcast(actor, arrivalBroadcast);
		return arrivalBroadcast;
	}
	
	@Override
	public String getDescription() {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(target, ws.getCurrentTime());
		String destDesc = !looks.isEmpty() ? looks.get(0).getDescription() : "somewhere";
		
		return "moving to " + destDesc;
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
