package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * Generic plant item that can be customized with different descriptions.
 * Covers: grass, wildflowers, mushrooms, moss, leaves, feathers, etc.
 */
public class Plant extends Item {
	
	public Plant(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a plant with the specified description.
	 * Adds basic look automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the plant (e.g., "some grass", "a wildflower")
	 * @return The created and configured plant entity
	 */
	public static Plant create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		Plant plant = es.add(Plant.class);
		ls.addLook(plant, "basic", description);
		
		return plant;
	}
}
