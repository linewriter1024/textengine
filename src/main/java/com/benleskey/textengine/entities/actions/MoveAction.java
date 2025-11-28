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
				CommandOutput.make("go")
					.error("nowhere")
					.text(Markup.escape("You are nowhere.")));
		}
		
		// Check if destination still exists
		try {
			es.get(target.getId());
		} catch (Exception e) {
			return ActionValidation.failure(
				CommandOutput.make("go")
					.error("destination_not_found")
					.text(Markup.escape("That destination doesn't exist.")));
		}
		
		return ActionValidation.success();
	}
	
	@Override
	public boolean execute() {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Get current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return false; // Actor has no location
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get actor description
		String actorDesc = getActorDescription(ls, ws);
		
		// Broadcast departure to entities in current location
		bs.broadcast(actor, CommandOutput.make("actor_leaves")
			.put("actor_id", actor.getKeyId())
			.put("actor_name", actorDesc)
			.put("from", currentLocation.getKeyId())
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
		bs.broadcast(actor, CommandOutput.make("actor_arrives")
			.put("actor_id", actor.getKeyId())
			.put("actor_name", actorDesc)
			.put("to", target.getKeyId())
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" arrives.")
			)));
		
		return true;
	}
	
	@Override
	public String getDescription() {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(target, ws.getCurrentTime());
		String destDesc = !looks.isEmpty() ? looks.get(0).getDescription() : "somewhere";
		
		return "moving to " + destDesc;
	}
	
	private String getActorDescription(LookSystem ls, WorldSystem ws) {
		List<LookDescriptor> looks = ls.getLooksFromEntity(actor, ws.getCurrentTime());
		if (!looks.isEmpty()) {
			String desc = looks.get(0).getDescription();
			// For NPCs, add article if needed
			if (!desc.startsWith("a ") && !desc.startsWith("an ") && !desc.startsWith("the ")) {
				return "a " + desc;
			}
			return desc;
		}
		return "someone";
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
