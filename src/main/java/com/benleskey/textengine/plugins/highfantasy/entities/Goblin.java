package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.systems.PendingActionSystem.PendingAction;

import java.util.List;
import java.util.Random;

/**
 * Goblin NPC - patrols between locations and interacts with items.
 * 
 * Simplified NPC that delegates all action logic to ActorActionSystem.
 * Only responsible for:
 * - Deciding what action to take (AI logic)
 * - Queueing actions via ActorActionSystem
 * - Reacting when actions complete
 */
public class Goblin extends Actor implements Tickable {
	
	private final Random random;
	
	public Goblin(long id, Game game) {
		super(id, game);
		this.random = new Random(id);
	}
	
	public static Goblin create(Game game, Entity startLocation, Entity roomA, Entity roomB) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
		
		Goblin goblin = es.add(Goblin.class);
		
		UniqueType patrolTarget = uts.getType("patrol_target");
		rs.add(goblin, roomA, patrolTarget);
		rs.add(goblin, roomB, patrolTarget);
		
		ls.addLook(goblin, "basic", "a goblin");
		es.addTag(goblin, es.TAG_TICKABLE);
		rs.add(startLocation, goblin, rs.rvContains);
		
		return goblin;
	}
	
	@Override
	public DTime getTickInterval() {
		return DTime.fromSeconds(120);
	}
	
	@Override
	public void onTick(DTime currentTime, DTime timeSinceLastTick) {
		PendingActionSystem pas = game.getSystem(PendingActionSystem.class);
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		
		// Check if we have a pending action
		PendingAction pending = pas.getPendingAction(this);
		
		if (pending != null) {
			// Wait for action to be ready
			if (pending.isReady(currentTime)) {
				log.log("Goblin %d: action %s ready, executing", getId(), pending.type);
				
				EntitySystem es = game.getSystem(EntitySystem.class);
				Entity target = es.get(pending.targetEntityId);
				
				// Execute via ActorActionSystem
				boolean success = aas.executeAction(this, pending.type, target, pending.timeRequired);
				
				// Clear the action
				pas.clearPendingAction(this);
				
				log.log("Goblin %d: action completed: %s", getId(), success ? "success" : "failed");
			} else {
				log.log("Goblin %d: still working on %s", getId(), pending.type);
			}
			return;
		}
		
		// No pending action - decide what to do next
		LookSystem ls = game.getSystem(LookSystem.class);
		LookSystem.LookEnvironment env = ls.getLookEnvironment(this);
		
		if (env == null) {
			log.log("Goblin %d has no location", getId());
			return;
		}
		
		// Randomly choose between moving and item actions
		if (random.nextBoolean()) {
			decideMove(env, aas);
		} else {
			decideItemAction(env, aas);
		}
	}
	
	/**
	 * AI: Decide where to move.
	 * Prefers patrol targets, falls back to random exits.
	 */
	private void decideMove(LookSystem.LookEnvironment env, ActorActionSystem aas) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
		
		UniqueType patrolTarget = uts.getType("patrol_target");
		List<Entity> patrolTargets = rs.getReceivingRelationships(this, patrolTarget, ws.getCurrentTime())
			.stream()
			.map(rd -> rd.getReceiver())
			.filter(t -> t.getId() != env.currentLocation.getId()) // Don't go to current location
			.toList();
		
		Entity destination;
		if (!patrolTargets.isEmpty()) {
			destination = patrolTargets.get(random.nextInt(patrolTargets.size()));
			log.log("Goblin %d: queueing patrol move to %d", getId(), destination.getId());
		} else if (!env.exits.isEmpty()) {
			destination = env.exits.get(random.nextInt(env.exits.size()));
			log.log("Goblin %d: queueing random move to %d", getId(), destination.getId());
		} else {
			log.log("Goblin %d: no exits available", getId());
			return;
		}
		
		// Queue move via ActorActionSystem
		aas.queueAction(this, aas.ACTION_MOVE, destination, DTime.fromSeconds(60));
	}
	
	/**
	 * AI: Decide what to do with items.
	 * Randomly takes or drops items.
	 */
	private void decideItemAction(LookSystem.LookEnvironment env, ActorActionSystem aas) {
		ItemSystem is = game.getSystem(ItemSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Filter out containers from items we can pick up
		List<Entity> pickupableItems = env.itemsHere.stream()
			.filter(e -> !is.hasTag(e, is.TAG_CONTAINER, ws.getCurrentTime()))
			.toList();
		
		// Decide: drop if carrying items, otherwise take
		if (!env.itemsCarried.isEmpty() && (pickupableItems.isEmpty() || random.nextBoolean())) {
			Entity itemToDrop = env.itemsCarried.get(random.nextInt(env.itemsCarried.size()));
			log.log("Goblin %d: queueing drop of item %d", getId(), itemToDrop.getId());
			aas.queueAction(this, aas.ACTION_ITEM_DROP, itemToDrop, DTime.fromSeconds(30));
		} else if (!pickupableItems.isEmpty()) {
			Entity itemToTake = pickupableItems.get(random.nextInt(pickupableItems.size()));
			log.log("Goblin %d: queueing take of item %d", getId(), itemToTake.getId());
			aas.queueAction(this, aas.ACTION_ITEM_TAKE, itemToTake, DTime.fromSeconds(30));
		} else {
			log.log("Goblin %d: nothing to do with items", getId());
		}
	}
}
