package com.benleskey.textengine.entities;

import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;

import java.util.List;

/**
 * Interface for entities that can tick (receive periodic updates).
 * Entities implementing this interface should also be tagged with TAG_TICKABLE.
 * The TickSystem will call onTick when enough time has elapsed.
 * 
 * To send output to nearby entities, use BroadcastSystem:
 * game.getSystem(BroadcastSystem.class).broadcast(this, commandOutput)
 */
public interface Tickable {
	
	/**
	 * Called when this entity should tick.
	 * To send output to nearby entities, use BroadcastSystem.broadcast().
	 * 
	 * @param currentTime The current game time
	 * @param timeSinceLastTick The amount of time that has passed since the last tick
	 */
	void onTick(DTime currentTime, DTime timeSinceLastTick);
	
	/**
	 * How often this entity should tick.
	 * For example, DTime.fromSeconds(60) means tick every minute.
	 * 
	 * @return The interval between ticks
	 */
	DTime getTickInterval();
}
