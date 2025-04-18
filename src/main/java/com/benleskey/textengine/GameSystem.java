package com.benleskey.textengine;

import com.benleskey.textengine.util.Logger;

import java.util.Set;

public abstract class GameSystem implements IGameSystem {
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

	@Override
	public String toString() {
		return String.format("%s#%s", this.getClass().getSimpleName(), getId());
	}

	public Set<GameSystem> getDependencies() {
		return Set.of();
	}

	@Override
	public int getEventOrder() {
		return 1 + this.getDependencies().stream().mapToInt(GameSystem::getEventOrder).sum();
	}
}
