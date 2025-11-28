package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.model.Entity;

/**
 * Represents an item in the game world.
 * Items can be picked up, dropped, examined, and contained within places or
 * actors.
 * Examples: stones, grass, swords, containers, etc.
 */
public class Item extends Entity {

	public Item(long id, Game game) {
		super(id, game);
	}
}
