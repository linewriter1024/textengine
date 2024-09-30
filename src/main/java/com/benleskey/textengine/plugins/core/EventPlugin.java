package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.systems.*;

public class EventPlugin extends Plugin {

	public EventPlugin(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerSystem(new EventSystem(game));
	}
}
