package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * A rusty old sword, still potentially usable as a weapon.
 */
public class RustySword extends Item {

	private static final String[] DESCRIPTIONS = {
			"a rusty sword",
			"a broken blade",
			"a bent sword"
	};

	public RustySword(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a rusty sword with a randomly selected description variant.
	 * 
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured sword entity
	 */
	public static RustySword create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		RustySword sword = es.add(RustySword.class);
		ls.addLook(sword, "basic", description);
		is.addTag(sword, is.TAG_TAKEABLE);
		is.addTag(sword, is.TAG_WEIGHT, 1500); // 1.5kg

		return sword;
	}
}
