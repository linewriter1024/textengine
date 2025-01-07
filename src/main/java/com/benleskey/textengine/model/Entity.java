package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;

public abstract class Entity extends Reference {
	public Entity(long id, Game game) {
		super(id, game);
	}

	public UniqueType getEntityType() {
		return game.getUniqueTypeSystem().getType(this.getClass().getCanonicalName());
	}
}
