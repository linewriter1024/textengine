package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.systems.*;

import java.util.Set;

public class EntityPlugin extends Plugin implements OnPluginInitialize {

	public EntityPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EventPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		// Create properties subsystem for connection exit names
		PropertiesSubSystem<Long, String, String> exitProperties = game.registerSystem(
			new PropertiesSubSystem<>(
				game,
				"connection_exit_properties",
				PropertiesSubSystem.longHandler(),
				PropertiesSubSystem.stringHandler(),
				PropertiesSubSystem.stringHandler()
			)
		);
		
		game.registerSystem(new EntitySystem(game));
		game.registerSystem(new LookSystem(game));
		game.registerSystem(new PositionSystem(game));
		game.registerSystem(new RelationshipSystem(game));
		game.registerSystem(new EntityTagSystem(game));
		game.registerSystem(new ConnectionSystem(game, exitProperties));
		game.registerSystem(new VisibilitySystem(game));
	}
}
