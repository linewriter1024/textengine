package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.systems.PendingActionSystem.PendingAction;

import java.util.List;
import java.util.Random;

public class Goblin extends Actor implements Tickable {
	
	private final Random random;
	
	public Goblin(long id, Game game) {
		super(id, game);
		log.log("Goblin %d constructor called", id);
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
		LookSystem ls = game.getSystem(LookSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);
		
		PendingAction pending = pas.getPendingAction(this);
		
		if (pending != null) {
			if (pending.isReady(currentTime)) {
				log.log("Goblin %d executing %s action", getId(), pending.type);
				executeAction(pending, aas, es);
				pas.clearPendingAction(this);
			} else {
				log.log("Goblin %d still working on %s", getId(), pending.type);
				return;
			}
		} else {
			// Check if we already have a pending action before queueing a new one
			PendingActionSystem.PendingAction existingAction = pas.getPendingAction(this);
			if (existingAction != null) {
				log.log("Goblin %d already has pending action %s, skipping new action", getId(), existingAction.type);
				return;
			}
		}
		
		LookSystem.LookEnvironment env = ls.getLookEnvironment(this);
		if (env == null) {
			log.log("Goblin %d has no location", getId());
			return;
		}
		
		if (random.nextBoolean()) {
			queueMove(env, pas);
		} else {
			queueItemAction(env, pas);
		}
	}
	
	private void queueMove(LookSystem.LookEnvironment env, PendingActionSystem pas) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
		
		UniqueType patrolTarget = uts.getType("patrol_target");
		List<Entity> patrolTargets = rs.getReceivingRelationships(this, patrolTarget, ws.getCurrentTime())
			.stream()
			.map(rd -> rd.getReceiver())
			.toList();		Entity destination;
		if (patrolTargets.isEmpty()) {
			if (env.exits.isEmpty()) {
				log.log("Goblin %d has no exits", getId());
				return;
			}
			destination = env.exits.get(random.nextInt(env.exits.size()));
			log.log("Goblin %d queueing random move to %d", getId(), destination.getId());
		} else {
			List<Entity> validTargets = patrolTargets.stream()
				.filter(t -> t.getId() != env.currentLocation.getId())
				.toList();
			
			if (!validTargets.isEmpty()) {
				destination = validTargets.get(random.nextInt(validTargets.size()));
				log.log("Goblin %d queueing patrol move to %d", getId(), destination.getId());
			} else {
				log.log("Goblin %d already at all patrol targets", getId());
				return;
			}
		}
		
		pas.queueAction(this, pas.ACTION_MOVE, DTime.fromSeconds(60), destination);
	}
	
	private void queueItemAction(LookSystem.LookEnvironment env, PendingActionSystem pas) {
		ItemSystem is = game.getSystem(ItemSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		List<Entity> pickupableItems = env.itemsHere.stream()
			.filter(e -> !is.hasTag(e, is.TAG_CONTAINER, ws.getCurrentTime()))
			.toList();
		
		if (!env.itemsCarried.isEmpty() && (pickupableItems.isEmpty() || random.nextBoolean())) {
			Entity itemToDrop = env.itemsCarried.get(random.nextInt(env.itemsCarried.size()));
			log.log("Goblin %d queueing drop of item %d", getId(), itemToDrop.getId());
			pas.queueAction(this, pas.ACTION_ITEM_DROP, DTime.fromSeconds(30), itemToDrop);
		} else if (!pickupableItems.isEmpty()) {
			Entity itemToTake = pickupableItems.get(random.nextInt(pickupableItems.size()));
			log.log("Goblin %d queueing take of item %d", getId(), itemToTake.getId());
			pas.queueAction(this, pas.ACTION_ITEM_TAKE, DTime.fromSeconds(30), itemToTake);
		} else {
			log.log("Goblin %d has nothing to do with items", getId());
		}
	}
	
	private void executeAction(PendingAction action, ActorActionSystem aas, EntitySystem es) {
		Entity target = es.get(action.targetEntityId);
		PendingActionSystem pas = game.getSystem(PendingActionSystem.class);
		
		if (action.type.equals(pas.ACTION_MOVE)) {
			log.log("Goblin %d moving to %d", getId(), target.getId());
			aas.moveActor(this, target, action.timeRequired);
		} else if (action.type.equals(pas.ACTION_ITEM_TAKE)) {
			log.log("Goblin %d taking item %d", getId(), target.getId());
			aas.takeItem(this, (Item) target, null);
		} else if (action.type.equals(pas.ACTION_ITEM_DROP)) {
			log.log("Goblin %d dropping item %d", getId(), target.getId());
			aas.dropItem(this, (Item) target);
		}
	}
}
