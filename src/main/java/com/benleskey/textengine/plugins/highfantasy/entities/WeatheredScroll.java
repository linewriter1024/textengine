package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * A weathered scroll with ancient writings.
 */
public class WeatheredScroll extends Item {
	
	private static final String[] DESCRIPTIONS = {
		"a weathered scroll",
		"a torn parchment",
		"a yellowed scroll"
	};
	
	public WeatheredScroll(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a weathered scroll with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured scroll entity
	 */
	public static WeatheredScroll create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		WeatheredScroll scroll = es.add(WeatheredScroll.class);
		ls.addLook(scroll, "basic", description);
		is.addTag(scroll, is.TAG_READABLE);
		is.addTag(scroll, is.TAG_TAKEABLE);
		is.addTag(scroll, is.TAG_WEIGHT, 20); // 20g
		
		return scroll;
	}
}
