package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.*;

import java.util.List;
import java.util.Random;

/**
 * A simple goblin NPC that wanders randomly and picks up/drops items.
 * Demonstrates how NPCs can use the same ActorActionSystem and LookSystem as players.
 */
public class Goblin extends Actor implements Tickable {
	
	private final Random random;
	private long lastActionTime;
	
	public Goblin(long id, Game game) {
		super(id, game);
		this.random = new Random(id); // Deterministic based on entity ID
		this.lastActionTime = 0;
	}
	
	/**
	 * Create a new goblin entity and place it in the world.
	 * 
	 * @param game The game instance
	 * @param startLocation The initial location for the goblin
	 * @return The created goblin
	 */
	public static Goblin create(Game game, Entity startLocation) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		
		Goblin goblin = es.add(Goblin.class);
		
		// Give it a description
		ls.addLook(goblin, "basic", "a goblin");
		
		// Mark as tickable so TickSystem will call onTick()
		es.addTag(goblin, es.TAG_TICKABLE);
		
		// Place in starting location
		rs.add(startLocation, goblin, rs.rvContains);
		
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
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		DTime now = ws.getCurrentTime();
		
		// Prevent acting too quickly if time jumps backward
		if (now.toMilliseconds() < lastActionTime) {
			lastActionTime = now.toMilliseconds();
			return;
		}
		
		// Use LookSystem to observe environment (same as player "look" command)
		LookSystem.LookEnvironment env = ls.getLookEnvironment(this);
		if (env == null) {
			return; // Goblin is nowhere - can't act
		}
		
		// Decide what to do: 50% move, 50% interact with items
		boolean shouldMove = random.nextBoolean();
		
		if (shouldMove) {
			tryMove(env, aas);
		} else {
			tryItemAction(env, aas);
		}
		
		lastActionTime = now.toMilliseconds();
	}

	/**
	 * Try to move to a random adjacent location.
	 */
	private void tryMove(LookSystem.LookEnvironment env, ActorActionSystem aas) {
		if (env.exits.isEmpty()) {
			return; // Nowhere to go
		}
		
		// Pick a random exit
		Entity destination = env.exits.get(random.nextInt(env.exits.size()));
		
		// Use ActorActionSystem to handle the movement (broadcasts automatically)
		// Movement takes 60 seconds for the goblin (tracked in lastActionTime, doesn't affect world)
		aas.moveActor(this, destination, DTime.fromSeconds(60));
	}	/**
	 * Try to pick up or drop an item.
	 */
	private void tryItemAction(LookSystem.LookEnvironment env, ActorActionSystem aas) {
		ItemSystem is = game.getSystem(ItemSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Filter out containers from items here (NPCs don't pick up containers)
		List<Entity> pickupableItems = env.itemsHere.stream()
			.filter(e -> !is.hasTag(e, is.TAG_CONTAINER, ws.getCurrentTime()))
			.toList();
		
		// Decide: if carrying items, maybe drop one; if items available, maybe pick one up
		if (!env.itemsCarried.isEmpty() && (pickupableItems.isEmpty() || random.nextBoolean())) {
			// Drop a random carried item
			Entity itemToDrop = env.itemsCarried.get(random.nextInt(env.itemsCarried.size()));
			aas.dropItem(this, (Item) itemToDrop);
		} else if (!pickupableItems.isEmpty()) {
			// Pick up a random item from location
			Entity itemToTake = pickupableItems.get(random.nextInt(pickupableItems.size()));
			aas.takeItem(this, (Item) itemToTake, null);
		}
	}
}
