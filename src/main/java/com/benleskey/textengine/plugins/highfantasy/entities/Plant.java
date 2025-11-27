package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * Generic plant item with variant descriptions.
 * Covers: grass, wildflowers, mushrooms, moss, leaves, feathers, etc.
 */
public class Plant extends Item {
	
	private static final String[] DESCRIPTIONS = {
		"some grass",
		"a wildflower",
		"some wild mushrooms",
		"a bird's feather",
		"a wet leaf",
		"some scraggly moss",
		"a dried herb"
	};
	
	public Plant(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a plant with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured plant entity
	 */
	public static Plant create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		Plant plant = es.add(Plant.class);
		ls.addLook(plant, "basic", description);
		is.addTag(plant, is.TAG_TAKEABLE);
		is.addTag(plant, is.TAG_WEIGHT, 50); // 50g
		
		return plant;
	}
}
