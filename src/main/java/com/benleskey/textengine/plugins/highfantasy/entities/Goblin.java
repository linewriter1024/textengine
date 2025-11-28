package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.model.ConnectionDescriptor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.RelationshipDescriptor;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Random;

/**
 * A goblin NPC that wanders between two rooms, picking up items and moving them.
 * Uses the same relationship and movement systems as the player.
 * Broadcasts its actions so nearby players can see what it does.
 */
public class Goblin extends Actor implements Tickable {
	
	private final Random random;
	private int[] roomA;  // First room coordinates (continent scale)
	private int[] roomB;  // Second room coordinates (continent scale)
	private long lastActionTime = 0;
	
	public Goblin(long id, Game game) {
		super(id, game);
		// Use entity ID as seed for deterministic but varied behavior
		this.random = new Random(id);
	}
	
	/**
	 * Create a goblin with proper tags and starting location.
	 * 
	 * @param game The game instance
	 * @param startLocation The place where the goblin starts
	 * @param roomA First room coordinates for wandering
	 * @param roomB Second room coordinates for wandering
	 * @return A new goblin entity
	 */
	public static Goblin create(Game game, Entity startLocation, int[] roomA, int[] roomB) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		
		Goblin goblin = es.add(Goblin.class);
		ls.addLook(goblin, "basic", "a goblin");
		game.log.log("[GOBLIN DEBUG] Created goblin entity %d", goblin.getId());
		
		// Mark as actor and tickable
		es.addTag(goblin, es.TAG_ACTOR);
		es.addTag(goblin, es.TAG_TICKABLE);
		game.log.log("[GOBLIN DEBUG] Tagged goblin %d as ACTOR and TICKABLE", goblin.getId());
		
		// Can carry items (5kg capacity)
		is.addTag(goblin, is.TAG_CARRY_WEIGHT, 5000L);
		
		// Place in starting location
		rs.add(startLocation, goblin, rs.rvContains);
		
		// Set wandering rooms
		goblin.roomA = roomA;
		goblin.roomB = roomB;
		
		return goblin;
	}
	
	@Override
	public DTime getTickInterval() {
		// Tick every 2 minutes (about as fast as player actions)
		return DTime.fromSeconds(120);
	}
	
	@Override
	public void onTick(DTime currentTime, DTime timeSinceLastTick) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		SpatialSystem ss = game.getSystem(SpatialSystem.class);
		ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		DTime now = ws.getCurrentTime();
		
		// Prevent acting too quickly if time jumps backward
		if (now.toMilliseconds() < lastActionTime) {
			lastActionTime = now.toMilliseconds();
			return;
		}
		
		// Get current location
		var containers = rs.getProvidingRelationships(this, rs.rvContains, now);
		if (containers.isEmpty()) {
			return; // Goblin is nowhere - can't act
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		int[] currentPos = ss.getPosition(currentLocation, SpatialSystem.SCALE_CONTINENT);
		
		if (currentPos == null) {
			return; // Location has no position
		}
		
		// Decide what to do: 50% move, 50% interact with items
		boolean shouldMove = random.nextBoolean();
		if (shouldMove) {
			// Try to move
			tryMove(currentLocation, currentPos, now, rs, ss, cs, bs, ls);
		} else {
			// Try to pick up or drop item
			tryItemAction(currentLocation, now, rs, is, bs, ls);
		}
		
		lastActionTime = now.toMilliseconds();
	}
	
	/**
	 * Try to move to another room (preferring to move between roomA and roomB).
	 */
	private void tryMove(Entity currentLocation, int[] currentPos, DTime now, 
	                    RelationshipSystem rs, SpatialSystem ss, ConnectionSystem cs, 
	                    BroadcastSystem bs, LookSystem ls) {
		
		// Determine target position based on current position
		int[] targetPos = null;
		if (java.util.Arrays.equals(currentPos, roomA)) {
			targetPos = roomB;
		} else if (java.util.Arrays.equals(currentPos, roomB)) {
			targetPos = roomA;
		} else {
			// Not in either target room - pick randomly
			targetPos = random.nextBoolean() ? roomA : roomB;
		}
		
		// Get exits from current location
		List<ConnectionDescriptor> exits = cs.getConnections(currentLocation, now);
		if (exits.isEmpty()) {
			return; // Nowhere to go
		}
		
		// Try to find an exit that leads toward target
		Entity destination = null;
		for (ConnectionDescriptor exit : exits) {
			Entity exitDest = exit.getTo();
			int[] exitPos = ss.getPosition(exitDest, SpatialSystem.SCALE_CONTINENT);
			if (exitPos != null && java.util.Arrays.equals(exitPos, targetPos)) {
				destination = exitDest;
				break;
			}
		}
		
		// If no direct path to target, pick random exit
		if (destination == null) {
			destination = exits.get(random.nextInt(exits.size())).getTo();
		}
		
		// Broadcast departure from current location
		bs.broadcast(this, CommandOutput.make("goblin_leaves")
			.put("success", true)
			.put("actor", String.valueOf(getId()))
			.put("from", currentLocation.getKeyId())
			.text(Markup.escape("A goblin leaves.")));
		
		// Move the goblin - get current containment relationship
		var containers = rs.getProvidingRelationships(this, rs.rvContains, now);
		if (containers.isEmpty()) {
			return; // Can't move if not contained anywhere
		}
		var oldContainment = containers.get(0).getRelationship();
		game.getSystem(EventSystem.class).cancelEvent(oldContainment);
		rs.add(destination, this, rs.rvContains);
		
		// Broadcast arrival at new location
		bs.broadcast(this, CommandOutput.make("goblin_arrives")
			.put("success", true)
			.put("actor", String.valueOf(getId()))
			.put("to", destination.getKeyId())
			.text(Markup.escape("A goblin arrives.")));
	}
	
	/**
	 * Try to pick up an item or drop a carried item.
	 */
	private void tryItemAction(Entity currentLocation, DTime now, 
	                          RelationshipSystem rs, ItemSystem is, 
	                          BroadcastSystem bs, LookSystem ls) {
		
		// Get carried items
		List<Entity> carriedItems = rs.getReceivingRelationships(this, rs.rvContains, now)
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.toList();
		
		// Get items in current location
		List<Entity> itemsHere = rs.getReceivingRelationships(currentLocation, rs.rvContains, now)
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.filter(e -> e.getId() != this.getId()) // Don't pick up self
			.toList();
		
		// Decide: drop if carrying something, pick up if hands are free
		if (!carriedItems.isEmpty() && random.nextBoolean()) {
			// Drop a random carried item
			Entity itemToDrop = carriedItems.get(random.nextInt(carriedItems.size()));
			
			// Get relationship and cancel it
			var carryRelationship = rs.getReceivingRelationships(this, rs.rvContains, now)
				.stream()
				.filter(rd -> rd.getReceiver().getId() == itemToDrop.getId())
				.findFirst();
			
			if (carryRelationship.isPresent()) {
				game.getSystem(EventSystem.class).cancelEvent(carryRelationship.get().getRelationship());
				rs.add(currentLocation, itemToDrop, rs.rvContains);
				
				String itemDesc = getItemDescription(itemToDrop, ls, now);
				bs.broadcast(this, CommandOutput.make("goblin_drops")
					.put("success", true)
					.put("actor", String.valueOf(getId()))
					.put("item", itemToDrop.getKeyId())
					.text(Markup.concat(
						Markup.raw("A goblin drops "),
						Markup.em(itemDesc),
						Markup.raw(".")
					)));
			}
		} else if (!itemsHere.isEmpty()) {
			// Try to pick up a random item
			Entity itemToTake = itemsHere.get(random.nextInt(itemsHere.size()));
			
			// Check if item is takeable and within weight capacity
			if (is.hasTag(itemToTake, is.TAG_TAKEABLE, now)) {
				Long itemWeight = is.getTagValue(itemToTake, is.TAG_WEIGHT, now);
				Long carryCapacity = is.getTagValue(this, is.TAG_CARRY_WEIGHT, now);
				
				if (itemWeight != null && carryCapacity != null) {
					// Calculate current carried weight
					long currentWeight = carriedItems.stream()
						.mapToLong(item -> {
							Long w = is.getTagValue(item, is.TAG_WEIGHT, now);
							return w != null ? w : 0L;
						})
						.sum();
					
					if (currentWeight + itemWeight <= carryCapacity) {
						// Take the item
						var locationRelationship = rs.getReceivingRelationships(currentLocation, rs.rvContains, now)
							.stream()
							.filter(rd -> rd.getReceiver().getId() == itemToTake.getId())
							.findFirst();
						
						if (locationRelationship.isPresent()) {
							game.getSystem(EventSystem.class).cancelEvent(locationRelationship.get().getRelationship());
							rs.add(this, itemToTake, rs.rvContains);
							
							String itemDesc = getItemDescription(itemToTake, ls, now);
							bs.broadcast(this, CommandOutput.make("goblin_takes")
								.put("success", true)
								.put("actor", String.valueOf(getId()))
								.put("item", itemToTake.getKeyId())
								.text(Markup.concat(
									Markup.raw("A goblin takes "),
									Markup.em(itemDesc),
									Markup.raw(".")
								)));
						}
					}
				}
			}
		}
	}
	
	/**
	 * Get a description of an item for broadcasts.
	 */
	private String getItemDescription(Entity item, LookSystem ls, DTime now) {
		var looks = ls.getLooksFromEntity(item, now);
		return !looks.isEmpty() ? looks.get(0).getDescription() : "something";
	}
}
