package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.systems.UniqueTypeSystem;

public class CorePlugin extends Plugin implements OnInitialize {

	public CorePlugin(Game game) {
		super(game);
	}

	@Override
	public void onInitialize() {
		game.registerSystem(new UniqueTypeSystem(game));
	}
}
