package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.util.Markup;

import java.util.List;

/**
 * ActorActionSystem provides shared functionality for all actors (players and NPCs).
 * Centralizes movement and item interaction logic so both player commands and NPC AI
 * use exactly the same code paths and broadcast the same messages.
 */
public class ActorActionSystem extends SingletonGameSystem {
	
	public ActorActionSystem(Game game) {
		super(game);
	}
	
	/**
	 * Move an actor from their current location to a destination.
	 * Broadcasts departure and arrival messages to nearby entities.
	 * 
	 * @param actor The actor to move
	 * @param destination The destination place
	 * @param timeCost How long the movement takes (e.g., DTime.fromSeconds(60))
	 * @return true if movement succeeded, false if actor has no location
	 */
	public boolean moveActor(Actor actor, Entity destination, DTime timeCost) {
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
		String actorDesc = getActorDescription(actor);
		
		// Broadcast departure to entities in current location
		bs.broadcast(actor, CommandOutput.make("actor_leaves")
			.put("success", true)
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
		rs.add(destination, actor, rs.rvContains);
		
		// Broadcast arrival to entities in destination
		bs.broadcast(actor, CommandOutput.make("actor_arrives")
			.put("success", true)
			.put("actor_id", actor.getKeyId())
			.put("actor_name", actorDesc)
			.put("to", destination.getKeyId())
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" arrives.")
			)));
		
		// NOTE: Does not increment world time - player commands do that, NPCs track their own time
		
		return true;
	}
	
	/**
	 * Actor takes an item from their current location (or from a container).
	 * Broadcasts the action to nearby entities.
	 * 
	 * @param actor The actor taking the item
	 * @param item The item to take
	 * @param fromContainer The container to take from (null if taking from ground)
	 * @return true if successful, false if item not available
	 */
	public boolean takeItem(Actor actor, Item item, Entity fromContainer) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		// Verify item is where we expect it
		var itemContainers = rs.getProvidingRelationships(item, rs.rvContains, ws.getCurrentTime());
		if (itemContainers.isEmpty()) {
			return false; // Item has no container
		}
		
		Entity actualContainer = itemContainers.get(0).getProvider();
		if (fromContainer != null && !actualContainer.equals(fromContainer)) {
			return false; // Item not in expected container
		}
		
		// Get descriptions
		String actorDesc = getActorDescription(actor);
		String itemDesc = getItemDescription(item);
		String containerDesc = fromContainer != null ? getItemDescription(fromContainer) : null;
		
		// Remove from old container
		var oldContainment = itemContainers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Add to actor's inventory
		rs.add(actor, item, rs.rvContains);
		
		// Build broadcast message
		CommandOutput broadcast;
		if (fromContainer != null) {
			broadcast = CommandOutput.make("actor_takes_from")
				.put("success", true)
				.put("actor_id", actor.getKeyId())
				.put("actor_name", actorDesc)
				.put("item_id", item.getKeyId())
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
				.put("item_id", item.getKeyId())
				.put("item_name", itemDesc)
				.text(Markup.concat(
					Markup.escape(capitalize(actorDesc)),
					Markup.raw(" takes "),
					Markup.em(itemDesc),
					Markup.raw(".")
				));
		}
		
		bs.broadcast(actor, broadcast);
		
		// NOTE: Does not increment world time - player commands do that, NPCs track their own time
		
		return true;
	}
	
	/**
	 * Actor drops an item in their current location.
	 * Broadcasts the action to nearby entities.
	 * 
	 * @param actor The actor dropping the item
	 * @param item The item to drop
	 * @return true if successful, false if actor doesn't have item or has no location
	 */
	public boolean dropItem(Actor actor, Item item) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		
		// Verify actor has the item
		var itemContainers = rs.getProvidingRelationships(item, rs.rvContains, ws.getCurrentTime());
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
		String actorDesc = getActorDescription(actor);
		String itemDesc = getItemDescription(item);
		
		// Remove from actor
		var oldContainment = itemContainers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		
		// Add to current location
		rs.add(currentLocation, item, rs.rvContains);
		
		// Broadcast action
		bs.broadcast(actor, CommandOutput.make("actor_drops")
			.put("success", true)
			.put("actor_id", actor.getKeyId())
			.put("actor_name", actorDesc)
			.put("item_id", item.getKeyId())
			.put("item_name", itemDesc)
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" drops "),
				Markup.em(itemDesc),
				Markup.raw(".")
			)));
		
		// NOTE: Does not increment world time - player commands do that, NPCs track their own time
		
		return true;
	}
	
	/**
	 * Get the description of an actor for broadcast messages.
	 * Returns "you" if the actor has an article prefix (player), otherwise returns the look description.
	 */
	private String getActorDescription(Entity actor) {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
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
	
	/**
	 * Get the description of an item for broadcast messages.
	 */
	private String getItemDescription(Entity item) {
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
		return !looks.isEmpty() ? looks.get(0).getDescription() : "something";
	}
	
	/**
	 * Capitalize the first letter of a string.
	 */
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
