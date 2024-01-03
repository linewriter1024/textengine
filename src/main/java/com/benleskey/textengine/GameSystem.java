package com.benleskey.textengine;

import com.benleskey.textengine.exceptions.DatabaseException;

public abstract class GameSystem {
	protected Game game;

	public GameSystem(Game game) {
		this.game = game;
	}

	public String getId() {
		return this.getClass().getCanonicalName();
	}

	public SchemaManager.Schema getSchema() {
		return game.getSchemaManager().getSchema(getId());
	}

	public abstract void initialize() throws DatabaseException;
}
