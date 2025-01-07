package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;

public class Place extends Entity {
	public Place(long id, Game game) {
		super(id, game);
	}

	public static Place create(Game game) throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		Place place = es.add(Place.class);
		ls.addLook(place, "basic", "a place");
		return place;
	}
}
