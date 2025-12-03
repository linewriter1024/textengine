package com.benleskey.textengine.plugins.games.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * Generic wood item with variant descriptions.
 * Covers: fallen branches, driftwood, twisted roots, etc.
 */
public class Wood extends Item {

	private static final String[] DESCRIPTIONS = {
			"a fallen branch",
			"a piece of driftwood",
			"a twisted root",
			"a weathered plank",
			"a gnarled stick"
	};

	public Wood(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a wood item with a randomly selected description variant.
	 * 
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured wood entity
	 */
	public static Wood create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		Wood wood = es.add(Wood.class);
		ls.addLook(wood, ls.LOOK_BASIC, description);
		is.addTag(wood, is.TAG_TAKEABLE);
		is.addTag(wood, is.TAG_WEIGHT, 300); // 300g

		return wood;
	}
}
