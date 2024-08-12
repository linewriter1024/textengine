package com.benleskey.textengine;

import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.util.Logger;

public abstract class GameSystem {
	protected Game game;
	protected Logger log;

	public GameSystem(Game game) {
		this.game = game;
		this.log = game.log.withPrefix(this.getClass().getSimpleName());
	}

	public String getId() {
		return this.getClass().getCanonicalName();
	}

	public SchemaManager.Schema getSchema() {
		return game.getSchemaManager().getSchema(getId());
	}

	public abstract void initialize() throws DatabaseException;

	@Override
	public String toString() {
		return String.format("%s#%s", this.getClass().getSimpleName(), getId());
	}
}
