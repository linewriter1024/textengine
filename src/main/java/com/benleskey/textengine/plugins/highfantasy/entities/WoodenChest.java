package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * A wooden chest that can contain other items.
 */
public class WoodenChest extends Item {
	
	private static final String[] DESCRIPTIONS = {
		"a wooden chest",
		"an old chest",
		"a battered chest"
	};
	
	public WoodenChest(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a wooden chest with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured chest entity
	 */
	public static WoodenChest create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		WoodenChest chest = es.add(WoodenChest.class);
		ls.addLook(chest, "basic", description);
		is.addTag(chest, is.TAG_CONTAINER);
		is.addTag(chest, is.TAG_TAKEABLE);
		is.addTag(chest, is.TAG_WEIGHT, 5000); // 5kg
		
		return chest;
	}
}
