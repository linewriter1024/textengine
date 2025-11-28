package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.util.Logger;

public abstract class Entity extends Reference {
	protected final Logger log;

	public Entity(long id, Game game) {
		super(id, game);
		// Create entity-specific logger with class name and ID prefix
		this.log = game.log.withPrefix(this.getClass().getSimpleName() + "#" + id);
	}

	public UniqueType getEntityType() {
		return game.getUniqueTypeSystem().getType(this.getClass().getCanonicalName());
	}

	/**
	 * Receive a broadcast event from another entity.
	 * Default implementation does nothing.
	 * Subclasses can override to handle broadcasts (e.g., Actor relays to client).
	 * 
	 * @param output The broadcast output to receive
	 */
	public void receiveBroadcast(CommandOutput output) {
		// Default: do nothing
	}
}
