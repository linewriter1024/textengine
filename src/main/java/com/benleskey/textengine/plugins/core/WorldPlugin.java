package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Place;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;

public class WorldPlugin extends Plugin {
	private Place home;

	public WorldPlugin(Game game) {
		super(game);
	}

	@Override
	public void initialize() {
		game.registerSystem(new WorldSystem(game));
	}

	@Override
	public void start() throws InternalException {
		home = Place.create(game);
		log.log("Home: %s", home);
	}

	@Override
	public void startClient(Client client) throws InternalException {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);

		Actor actor = Actor.create(game);
		client.setEntity(actor);

		rs.add(home, actor, "contains");
	}
}
