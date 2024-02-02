package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.PositionSystem;
import com.benleskey.textengine.systems.RelationshipSystem;

public class EntityPlugin extends Plugin {

	public EntityPlugin(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerSystem(new EntitySystem(game));
		game.registerSystem(new LookSystem(game));
		game.registerSystem(new PositionSystem(game));
		game.registerSystem(new RelationshipSystem(game));
	}
}
