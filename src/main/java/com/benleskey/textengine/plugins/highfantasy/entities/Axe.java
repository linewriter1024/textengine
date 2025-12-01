package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * An axe that can cut down cuttable things (trees).
 * The axe has TAG_CUT and TAG_TOOL tags.
 * The generic tag system handles the cutting interaction.
 */
public class Axe extends Item {

	private static final String[] DESCRIPTIONS = {
			"a rusty axe",
			"a weathered hatchet",
			"a chipped axe"
	};

	public Axe(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create an axe with a randomly selected description variant.
	 * 
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured axe entity
	 */
	public static Axe create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		Axe axe = es.add(Axe.class);
		ls.addLook(axe, ls.LOOK_BASIC, description);
		is.addTag(axe, is.TAG_CUT);
		is.addTag(axe, is.TAG_TOOL);
		is.addTag(axe, is.TAG_TAKEABLE);
		is.addTag(axe, is.TAG_WEIGHT, 1200); // 1.2kg

		return axe;
	}
}
