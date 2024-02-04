package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;

public class WorldSystem extends SingletonGameSystem {
	private GrouplessPropertiesSubSystem<String, Long> referencePoints;

	public WorldSystem(Game game) {
		super(game);
		referencePoints = game.registerSystem(new GrouplessPropertiesSubSystem<>(game, "world_reference_points", PropertiesSubSystem.stringHandler(), PropertiesSubSystem.longHandler()));
	}

	@Override
	public void initialize() throws DatabaseException {
	}
}
