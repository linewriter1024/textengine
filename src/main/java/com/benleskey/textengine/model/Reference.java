package com.benleskey.textengine.model;

import com.benleskey.textengine.Game;

/**
 * Interface for all reference types in the game.
 * References are uniquely identified by their ID.
 * Use BaseReference for the standard implementation.
 */
public interface Reference {

	/**
	 * Get the unique ID of this reference.
	 */
	long getId();

	/**
	 * Get the game instance this reference belongs to.
	 */
	Game getGame();

	/**
	 * Get the string key ID for this reference (used in commands).
	 */
	default String getKeyId() {
		return Long.toString(getId());
	}
}
