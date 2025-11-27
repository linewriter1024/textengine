package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.util.Set;

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
	 * Process ticks for all tickable entities in the world.
	 * Called after time has advanced to trigger entity ticks based on their intervals.
	 */
	public void processWorldTicks() {
		DTime currentTime = worldSystem.getCurrentTime();
		
		// Validate TAG_TICKABLE exists
		UniqueType tagTickable = entitySystem.TAG_TICKABLE;
		if (tagTickable == null) {
			log.log("TAG_TICKABLE not initialized, skipping tick processing");
			return;
		}
		
		// Get all entities tagged as tickable
		Set<Entity> tickableEntities = tagSystem.findEntitiesByTag(tagTickable, currentTime);
		
		// Process each tickable entity
		for (Entity entity : tickableEntities) {
			if (entity instanceof Tickable tickable) {
				processEntityTick(tickable, entity, currentTime);
			}
		}
	}
	
	/**
	 * Process a tick for a specific tickable entity.
	 * 
	 * @param tickable The tickable entity
	 * @param entity The entity
	 * @param currentTime The current game time
	 */
	private void processEntityTick(Tickable tickable, Entity entity, DTime currentTime) {
		// Get last tick time from tag, or use entity creation time if never ticked
		Long lastTickMs = entitySystem.getTagValue(entity, entitySystem.TAG_LAST_TICK, currentTime);
		DTime lastTick;
		
		if (lastTickMs == null) {
			// Never ticked before - use entity creation time
			Long creationMs = entitySystem.getTagValue(entity, entitySystem.TAG_ENTITY_CREATED, currentTime);
			if (creationMs != null) {
				lastTick = new DTime(creationMs);
			} else {
				// Fallback: entity created before creation time tracking was added
				lastTick = new DTime(0);
			}
		} else {
			lastTick = new DTime(lastTickMs);
		}
		
		DTime interval = tickable.getTickInterval();
		DTime timeSinceLastTick = new DTime(currentTime.toMilliseconds() - lastTick.toMilliseconds());
		
		// Check if enough time has passed for a tick
		if (timeSinceLastTick.toMilliseconds() >= interval.toMilliseconds()) {
			// Calculate how many ticks should have occurred
			long tickCount = timeSinceLastTick.toMilliseconds() / interval.toMilliseconds();
			
			// Trigger the tick(s) with correct time for each tick
			for (int i = 0; i < tickCount; i++) {
				// Calculate the actual time of this specific tick
				DTime tickTime = new DTime(lastTick.toMilliseconds() + ((i + 1) * interval.toMilliseconds()));
				DTime timeSincePreviousTick = interval;
				tickable.onTick(tickTime, timeSincePreviousTick);
			}
			
			// Update last tick time as a tag (persisted and temporal)
			DTime newLastTick = new DTime(lastTick.toMilliseconds() + (tickCount * interval.toMilliseconds()));
			entitySystem.updateTagValue(entity, entitySystem.TAG_LAST_TICK, newLastTick.toMilliseconds(), currentTime);
		}
	}
}
