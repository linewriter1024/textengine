package com.benleskey.textengine.plugins;

import com.benleskey.textengine.EntitySystem;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;

public class EntityPlugin extends Plugin {

	public EntityPlugin(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerSystem(new EntitySystem(game));
	}
}
