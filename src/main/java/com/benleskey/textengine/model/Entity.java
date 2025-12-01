package com.benleskey.textengine.model;

import com.benleskey.textengine.commands.CommandOutput;

/**
 * Interface for all game entities.
 * Entities are References that are uniquely identified by their ID and have a
 * type.
 * Use BaseEntity for the standard implementation.
 */
public interface Entity extends Reference {

	/**
	 * Get the entity type as a UniqueType.
	 */
	UniqueType getEntityType();

	/**
	 * Receive a broadcast event from another entity.
	 * Default implementation does nothing.
	 * Subclasses can override to handle broadcasts (e.g., Actor relays to client).
	 * 
	 * @param output The broadcast output to receive
	 */
	default void receiveBroadcast(CommandOutput output) {
		// Default: do nothing
	}
}
