package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.GameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;

public class WorldSystem extends GameSystem {
	private GrouplessPropertiesSubSystem<String, Long> referencePoints;

	public WorldSystem(Game game) {
		super(game);
		referencePoints = game.registerSystem(new GrouplessPropertiesSubSystem<>(game,"world_reference_points", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void initialize() throws DatabaseException {
		referencePoints.set("test", 123L);
	}
}
