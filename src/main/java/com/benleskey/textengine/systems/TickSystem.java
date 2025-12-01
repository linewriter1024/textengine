package com.benleskey.textengine.systems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

/**
 * System for processing Acting entities over time.
 * Acting entities (players, NPCs, objects like clocks) have their actions
 * processed when world time advances.
 */
public class TickSystem extends SingletonGameSystem implements OnSystemInitialize {

	private EntityTagSystem tagSystem;
	private WorldSystem worldSystem;
	private EntitySystem entitySystem;

	public TickSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		tagSystem = game.getSystem(EntityTagSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);

		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - tick state is kept in memory
			getSchema().setVersionNumber(1);
		}
	}

	/**
	 * Process actions for all Acting entities in the world.
	 * Entities are processed in time order to ensure fairness.
	 */
	public void processWorldTicks() {
		DTime currentTime = worldSystem.getCurrentTime();
		ActionSystem aas = game.getSystem(ActionSystem.class);

		// Collect all Acting entities
		List<Acting> allActing = new ArrayList<>();

		UniqueType tagActing = aas.TAG_ACTING;
		if (tagActing != null) {
			Set<Entity> actingEntities = tagSystem.findEntitiesByTag(tagActing, currentTime);
			for (Entity entity : actingEntities) {
				if (entity instanceof Acting acting) {
					allActing.add(acting);
				}
			}
		}

		// Process all entities in time-fair order
		processEntitiesInTimeOrder(allActing, currentTime, aas);
	}

	/**
	 * Process entities one action at a time in chronological order.
	 * This ensures fair interleaving - no single entity monopolizes time.
	 */
	private void processEntitiesInTimeOrder(List<Acting> entities, DTime targetTime, ActionSystem aas) {
		PriorityQueue<ActingTick> tickQueue = new PriorityQueue<>(Comparator.comparingLong(t -> t.tickTime));

		for (Acting acting : entities) {
			Entity entity = (Entity) acting;
			Long lastTickMs = entitySystem.getTagValue(entity, aas.TAG_LAST_ACTION_CHECK, targetTime);
			DTime lastTick;

			if (lastTickMs == null) {
				Long creationMs = entitySystem.getTagValue(entity, entitySystem.TAG_ENTITY_CREATED, targetTime);
				lastTick = creationMs != null ? DTime.fromMilliseconds(creationMs) : DTime.fromMilliseconds(0);
			} else {
				lastTick = DTime.fromMilliseconds(lastTickMs);
			}

			DTime interval = acting.getActionInterval();
			long nextTickTime = lastTick.toMilliseconds() + interval.toMilliseconds();

			if (nextTickTime <= targetTime.toMilliseconds()) {
				tickQueue.offer(new ActingTick(acting, nextTickTime));
			}
		}

		while (!tickQueue.isEmpty()) {
			ActingTick tick = tickQueue.poll();
			Acting acting = tick.acting;
			Entity entity = (Entity) acting;
			DTime tickTime = DTime.fromMilliseconds(tick.tickTime);

			if (tickTime.toMilliseconds() > targetTime.toMilliseconds()) {
				break;
			}

			worldSystem.setCurrentTime(tickTime);

			DTime interval = acting.getActionInterval();
			aas.processActingEntitySingleTick(acting, interval);
			entitySystem.updateTagValue(entity, aas.TAG_LAST_ACTION_CHECK, tickTime.toMilliseconds(), targetTime);

			// Check if entity has a pending action and schedule tick for completion
			boolean scheduledActionTick = false;

			Action pendingAction = aas.getPendingAction(acting);
			if (pendingAction != null) {
				long actionReadyTime = aas.getActionReadyTime(pendingAction);
				if (actionReadyTime > tickTime.toMilliseconds()
						&& actionReadyTime <= targetTime.toMilliseconds()) {
					tickQueue.offer(new ActingTick(acting, actionReadyTime));
					scheduledActionTick = true;
				}
			}

			if (!scheduledActionTick) {
				long nextTickTime = tickTime.toMilliseconds() + interval.toMilliseconds();
				if (nextTickTime <= targetTime.toMilliseconds()) {
					tickQueue.offer(new ActingTick(acting, nextTickTime));
				}
			}
		}

		worldSystem.setCurrentTime(targetTime);
	}

	private static class ActingTick {
		Acting acting;
		long tickTime;

		ActingTick(Acting acting, long tickTime) {
			this.acting = acting;
			this.tickTime = tickTime;
		}
	}
}
