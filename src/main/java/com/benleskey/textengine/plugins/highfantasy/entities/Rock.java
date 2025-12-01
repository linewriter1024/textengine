package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * Generic rock/stone item with variant descriptions.
 * Covers: granite chunks, river stones, smooth pebbles, rubble, etc.
 */
public class Rock extends Item {

	private static final String[] DESCRIPTIONS = {
			"a chunk of granite",
			"a river stone",
			"a smooth pebble",
			"a piece of rubble",
			"a jagged shard"
	};

	public Rock(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a rock with a randomly selected description variant.
	 * 
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured rock entity
	 */
	public static Rock create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		Rock rock = es.add(Rock.class);
		ls.addLook(rock, ls.LOOK_BASIC, description);
		is.addTag(rock, is.TAG_TAKEABLE);
		is.addTag(rock, is.TAG_WEIGHT, 500); // 500g = 0.5kg

		return rock;
	}
}
