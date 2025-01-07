package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.*;

import java.util.Set;

public class EventPlugin extends Plugin implements OnPluginInitialize {

	public EventPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(CorePlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new EventSystem(game));
	}
}
