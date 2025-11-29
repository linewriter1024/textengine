package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.actions.ActionValidation;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Random;

/**
 * Goblin NPC - patrols between locations and interacts with items.
 * 
 * Implements Acting interface - ActorActionSystem calls onActionReady() when:
 * - No pending action exists
 * - Enough time has passed since last check (getActionInterval())
 * 
 * AI logic only - all execution handled by ActorActionSystem.
 */
public class Goblin extends Actor implements Acting {

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
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);

		Goblin goblin = es.add(Goblin.class);

		UniqueType patrolTarget = uts.getType("patrol_target");
		rs.add(goblin, roomA, patrolTarget);
		rs.add(goblin, roomB, patrolTarget);

		ls.addLook(goblin, "basic", "a goblin");
		es.addTag(goblin, aas.TAG_ACTING);
		rs.add(startLocation, goblin, rs.rvContains);

		return goblin;
	}

	@Override
	public DTime getActionInterval() {
		return DTime.fromSeconds(600); // 10 minutes
	}

	@Override
	public void onActionReady() {
		// AI: Decide what to do (no pending action, ready for new action)
		LookSystem ls = game.getSystem(LookSystem.class);
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		DTime currentTime = ws.getCurrentTime();
		log.log("onActionReady called at time %d", currentTime.toMilliseconds());

		LookSystem.LookEnvironment env = ls.getLookEnvironment(this);

		if (env == null) {
			log.log("has no location");
			return;
		}

		// Announce the time (instant action)
		long hours = (currentTime.toMilliseconds() / (1000 * 60 * 60)) % 24;
		long minutes = (currentTime.toMilliseconds() / (1000 * 60)) % 60;
		String timeStr = String.format("%02d:%02d", hours, minutes);

		CommandOutput announcement = new CommandOutput();
		announcement.text(Markup.raw(String.format("The goblin mutters '%s'.", timeStr)));
		bs.broadcast(this, announcement);

		log.log("announced time: %s", timeStr);

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
			log.log("queueing patrol move to %d", destination.getId());
		} else if (!env.exits.isEmpty()) {
			destination = env.exits.get(random.nextInt(env.exits.size()));
			log.log("queueing random move to %d", destination.getId());
		} else {
			log.log("no exits available");
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
			log.log("attempting drop of item %d", itemToDrop.getId());
			ActionValidation dropResult = aas.queueAction(this, aas.ACTION_ITEM_DROP, itemToDrop,
					DTime.fromSeconds(30));
			if (!dropResult.isValid()) {
				log.log("failed to drop item %d - %s", itemToDrop.getId(),
						dropResult.getErrorCode());
			}
		} else if (!pickupableItems.isEmpty()) {
			Entity itemToTake = pickupableItems.get(random.nextInt(pickupableItems.size()));
			log.log("attempting take of item %d", itemToTake.getId());
			ActionValidation takeResult = aas.queueAction(this, aas.ACTION_ITEM_TAKE, itemToTake,
					DTime.fromSeconds(30));
			if (!takeResult.isValid()) {
				log.log("failed to take item %d - %s", itemToTake.getId(),
						takeResult.getErrorCode());
			}
		} else {
			log.log("nothing to do with items");
		}
	}
}
