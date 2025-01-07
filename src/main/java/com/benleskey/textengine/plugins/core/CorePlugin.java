package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.UniqueTypeSystem;

public class CorePlugin extends Plugin implements OnPluginInitialize {

	public CorePlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new UniqueTypeSystem(game));
	}
}
