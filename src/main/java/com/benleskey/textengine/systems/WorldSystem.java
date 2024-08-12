package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.model.DTime;

public class WorldSystem extends SingletonGameSystem {

	private final static long TIME_NOW = 0;
	private GrouplessPropertiesSubSystem<String, Long> referencePoints;
	private GrouplessPropertiesSubSystem<Long, Long> time;
	private long currentTime;

	public WorldSystem(Game game) {
		super(game);
		referencePoints = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "world_reference_points", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
		time = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "world_time", PropertiesSubSystem.longHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void initialize() throws DatabaseException {
		currentTime = time.get(TIME_NOW).orElse(0L);
		log.log("The current world time is " + getCurrentTime());
	}

	public synchronized DTime getCurrentTime() throws DatabaseException {
		return new DTime(currentTime);
	}

	public synchronized DTime setCurrentTime(DTime newTime) throws DatabaseException {
		// Write-through cache.
		long newRawTime = newTime.getRaw();
		currentTime = newRawTime;
		time.set(TIME_NOW, newRawTime);
		return getCurrentTime();
	}

	public synchronized DTime incrementCurrentTime(DTime delta) throws DatabaseException {
		return setCurrentTime(getCurrentTime().add(delta));
	}
}
