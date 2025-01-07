package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;

public class Actor extends Entity {

	public Actor(long id, Game game) {
		super(id, game);
	}

	public static Actor create(Game game) throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		Actor actor = es.add(Actor.class);
		ls.addLook(actor, "basic", "an actor");
		return actor;
	}
}
