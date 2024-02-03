package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.systems.*;

public class WorldPlugin extends Plugin {

	public WorldPlugin(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerSystem(new WorldSystem(game));
	}
}
