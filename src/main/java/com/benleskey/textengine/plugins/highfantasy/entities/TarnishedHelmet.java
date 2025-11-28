package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * A tarnished helmet from an ancient warrior.
 */
public class TarnishedHelmet extends Item {

	private static final String[] DESCRIPTIONS = {
			"a tarnished helmet",
			"a dented helmet",
			"a battered helm"
	};

	public TarnishedHelmet(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a tarnished helmet with a randomly selected description variant.
	 * 
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured helmet entity
	 */
	public static TarnishedHelmet create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		TarnishedHelmet helmet = es.add(TarnishedHelmet.class);
		ls.addLook(helmet, "basic", description);
		is.addTag(helmet, is.TAG_TAKEABLE);
		is.addTag(helmet, is.TAG_WEIGHT, 2000); // 2kg

		return helmet;
	}
}
