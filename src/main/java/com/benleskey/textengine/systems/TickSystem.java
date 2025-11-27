package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.util.HashMap;
import java.util.Map;
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
	private ItemSystem itemSystem;
	
	// Track last tick time for each tickable entity
	private Map<Long, DTime> lastTickTimes = new HashMap<>();

	public TickSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		tagSystem = game.getSystem(EntityTagSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);
		
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
		UniqueType tagTickable = itemSystem.TAG_TICKABLE;
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
		DTime lastTick = lastTickTimes.get(entity.getId());
		
		if (lastTick == null) {
			// First time seeing this entity - initialize to world start time (0:00:00)
			// This ensures all ticks from world start to now will be processed
			lastTick = new DTime(0);
			lastTickTimes.put(entity.getId(), lastTick);
			// Don't return - continue to process ticks
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
			
			// Update last tick time
			DTime newLastTick = new DTime(lastTick.toMilliseconds() + (tickCount * interval.toMilliseconds()));
			lastTickTimes.put(entity.getId(), newLastTick);
		}
	}
}
