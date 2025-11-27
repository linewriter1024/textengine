package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * Generic rock/stone item that can be customized with different descriptions.
 * Covers: granite chunks, river stones, smooth pebbles, rubble, etc.
 */
public class Rock extends Item {
	
	public Rock(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a rock with the specified description.
	 * Adds basic look automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the rock (e.g., "a chunk of granite")
	 * @return The created and configured rock entity
	 */
	public static Rock create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		Rock rock = es.add(Rock.class);
		ls.addLook(rock, "basic", description);
		
		return rock;
	}
}
