package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;

public class WorldSystem extends SingletonGameSystem implements OnSystemInitialize {

	private final static long TIME_NOW = 0;
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
}
