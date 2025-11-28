package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;

public class WorldSystem extends SingletonGameSystem implements OnSystemInitialize {

	private final static long TIME_NOW = 0;
	private final static long WORLD_SEED = 1;
	private final static long WORLD_INITIALIZED = 2;
	
	// Common message field constants for time/world-related data
	public static final String M_DURATION = "duration";
	
	// Reserved for future use - may be used for temporal anchoring or waypoint system
	@SuppressWarnings("unused")
	private final GrouplessPropertiesSubSystem<String, Long> referencePoints;
	private final GrouplessPropertiesSubSystem<Long, Long> time;
	private long currentTime;

	public WorldSystem(Game game) {
		super(game);
		referencePoints = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "world_reference_point", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
		time = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "world_time", PropertiesSubSystem.longHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		currentTime = time.get(TIME_NOW).orElse(0L);
		log.log("The current world time is " + getCurrentTime());
	}

	public synchronized DTime getCurrentTime() throws DatabaseException {
		return new DTime(currentTime);
	}

	public synchronized DTime setCurrentTime(DTime newTime) throws DatabaseException {
		// Write-through cache.
		long newRawTime = newTime.raw();
		currentTime = newRawTime;
		time.set(TIME_NOW, newRawTime);
		return getCurrentTime();
	}

	public synchronized DTime incrementCurrentTime(DTime delta) throws DatabaseException {
		return setCurrentTime(getCurrentTime().add(delta));
	}
	
	/**
	 * Get the world generation seed.
	 * Returns null if seed has not been set (new world).
	 */
	public synchronized Long getSeed() throws DatabaseException {
		return time.get(WORLD_SEED).orElse(null);
	}
	
	/**
	 * Set the world generation seed.
	 * Should only be called once when creating a new world.
	 */
	public synchronized void setSeed(long seed) throws DatabaseException {
		time.set(WORLD_SEED, seed);
		log.log("World seed set to %d", seed);
	}
	
	/**
	 * Check if the world has been initialized.
	 * Returns true if world generation has been completed.
	 */
	public synchronized boolean isWorldInitialized() throws DatabaseException {
		return time.get(WORLD_INITIALIZED).orElse(0L) == 1L;
	}
	
	/**
	 * Mark the world as initialized.
	 * Should be called after initial world generation is complete.
	 */
	public synchronized void setWorldInitialized() throws DatabaseException {
		time.set(WORLD_INITIALIZED, 1L);
		log.log("World marked as initialized");
	}
}
