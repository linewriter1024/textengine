package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.CommandHelpSystem;
import com.benleskey.textengine.systems.SpatialSystem;
import com.benleskey.textengine.systems.TickSystem;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.systems.CommandCompletionSystem;
import com.benleskey.textengine.systems.WorldSystem;

public class CorePlugin extends Plugin implements OnPluginInitialize {

	public CorePlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new UniqueTypeSystem(game));
		game.registerSystem(new CommandHelpSystem(game));
		game.registerSystem(new CommandCompletionSystem(game));
		game.registerSystem(new WorldSystem(game));
		game.registerSystem(new TickSystem(game));
		game.registerSystem(new SpatialSystem(game));
	}
}
