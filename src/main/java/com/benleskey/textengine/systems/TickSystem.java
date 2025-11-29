package com.benleskey.textengine.systems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

/**
 * Generic system for handling entity ticks.
 * Entities implementing Tickable and tagged with TAG_TICKABLE will have their
 * onTick method called at their specified interval when world time advances.
 * Ticks are processed globally for all tickable entities, not per-client.
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
	 * Process ticks for all tickable and acting entities in the world.
	 * Entities are processed in time order to ensure fairness - each entity
	 * gets one tick/action at a time, interleaved chronologically.
	 */
	public void processWorldTicks() {
		DTime currentTime = worldSystem.getCurrentTime();
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);

		// Collect all entities that can tick (Tickable or Acting)
		List<TickableEntity> allEntities = new ArrayList<>();

		// Add Tickable entities
		UniqueType tagTickable = entitySystem.TAG_TICKABLE;
		if (tagTickable != null) {
			Set<Entity> tickableEntities = tagSystem.findEntitiesByTag(tagTickable, currentTime);
			for (Entity entity : tickableEntities) {
				if (entity instanceof Tickable tickable) {
					allEntities.add(new TickableEntity(entity, tickable, null, entitySystem.TAG_LAST_TICK));
				}
			}
		}

		// Add Acting entities
		UniqueType tagActing = aas.TAG_ACTING;
		if (tagActing != null) {
			Set<Entity> actingEntities = tagSystem.findEntitiesByTag(tagActing, currentTime);
			for (Entity entity : actingEntities) {
				if (entity instanceof Acting acting && entity instanceof Actor) {
					allEntities.add(new TickableEntity(entity, null, acting, aas.TAG_LAST_ACTION_CHECK));
				}
			}
		}

		// Process all entities in time-fair order
		processEntitiesInTimeOrder(allEntities, currentTime, aas);
	}

	/**
	 * Process entities one tick at a time in chronological order.
	 * This ensures fair interleaving - no single entity monopolizes time.
	 * Sets world time to each tick time as we process them.
	 */
	private void processEntitiesInTimeOrder(List<TickableEntity> entities, DTime targetTime, ActorActionSystem aas) {
		// Calculate next tick time for each entity
		PriorityQueue<EntityTick> tickQueue = new PriorityQueue<>(Comparator.comparingLong(t -> t.tickTime));

		for (TickableEntity te : entities) {
			Long lastTickMs = entitySystem.getTagValue(te.entity, te.lastTickTag, targetTime);
			DTime lastTick;

			if (lastTickMs == null) {
				// Never ticked before - use entity creation time
				Long creationMs = entitySystem.getTagValue(te.entity, entitySystem.TAG_ENTITY_CREATED, targetTime);
				lastTick = creationMs != null ? DTime.fromMilliseconds(creationMs) : DTime.fromMilliseconds(0);
			} else {
				lastTick = DTime.fromMilliseconds(lastTickMs);
			}

			DTime interval = te.tickable != null ? te.tickable.getTickInterval() : te.acting.getActionInterval();
			long nextTickTime = lastTick.toMilliseconds() + interval.toMilliseconds();

			// Only queue if next tick is due
			if (nextTickTime <= targetTime.toMilliseconds()) {
				tickQueue.offer(new EntityTick(te, nextTickTime));
			}
		}

		// Process ticks in chronological order, setting world time as we go
		while (!tickQueue.isEmpty()) {
			EntityTick tick = tickQueue.poll();
			TickableEntity te = tick.entity;
			DTime tickTime = DTime.fromMilliseconds(tick.tickTime);

			// Don't process ticks beyond target time
			if (tickTime.toMilliseconds() > targetTime.toMilliseconds()) {
				break;
			}

			// Set world time to this tick time - this is key!
			worldSystem.setCurrentTime(tickTime);

			// Execute the tick
			if (te.tickable != null) {
				DTime interval = te.tickable.getTickInterval();
				te.tickable.onTick(tickTime, interval);
				entitySystem.updateTagValue(te.entity, te.lastTickTag, tickTime.toMilliseconds(), targetTime);

				// Schedule next regular tick
				long nextTickTime = tickTime.toMilliseconds() + interval.toMilliseconds();
				if (nextTickTime <= targetTime.toMilliseconds()) {
					tickQueue.offer(new EntityTick(te, nextTickTime));
				}
			} else if (te.acting != null) {
				DTime interval = te.acting.getActionInterval();
				aas.processActingEntitySingleTick(te.acting, (Actor) te.entity, interval);
				entitySystem.updateTagValue(te.entity, te.lastTickTag, tickTime.toMilliseconds(), targetTime);

				// Check if entity has a pending action and schedule tick for completion
				boolean scheduledActionTick = false;

				Reference pendingAction = aas.getPendingAction((Actor) te.entity);
				if (pendingAction != null) {
					long actionReadyTime = aas.getActionReadyTime(pendingAction);
					if (actionReadyTime > tickTime.toMilliseconds()
							&& actionReadyTime <= targetTime.toMilliseconds()) {
						tickQueue.offer(new EntityTick(te, actionReadyTime));
						scheduledActionTick = true;
					}
				}

				// Only schedule regular interval tick if we didn't schedule an action
				// completion
				if (!scheduledActionTick) {
					long nextTickTime = tickTime.toMilliseconds() + interval.toMilliseconds();
					if (nextTickTime <= targetTime.toMilliseconds()) {
						tickQueue.offer(new EntityTick(te, nextTickTime));
					}
				}
			}
		} // Restore world time to target time
		worldSystem.setCurrentTime(targetTime);
	}

	/**
	 * Helper class to track entity tick scheduling.
	 */
	private static class TickableEntity {
		Entity entity;
		Tickable tickable;
		Acting acting;
		UniqueType lastTickTag;

		TickableEntity(Entity entity, Tickable tickable, Acting acting, UniqueType lastTickTag) {
			this.entity = entity;
			this.tickable = tickable;
			this.acting = acting;
			this.lastTickTag = lastTickTag;
		}
	}

	/**
	 * Helper class for priority queue ordering.
	 */
	private static class EntityTick {
		TickableEntity entity;
		long tickTime;

		EntityTick(TickableEntity entity, long tickTime) {
			this.entity = entity;
			this.tickTime = tickTime;
		}
	}

}
