package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * An ancient coin from a lost civilization.
 */
public class AncientCoin extends Item {
	
	private static final String[] DESCRIPTIONS = {
		"an ancient coin",
		"a tarnished coin",
		"a gold coin"
	};
	
	public AncientCoin(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create an ancient coin with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured coin entity
	 */
	public static AncientCoin create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		AncientCoin coin = es.add(AncientCoin.class);
		ls.addLook(coin, "basic", description);
		is.addTag(coin, is.TAG_CURRENCY);
		is.addTag(coin, is.TAG_TAKEABLE);
		is.addTag(coin, is.TAG_WEIGHT, 10); // 10g
		
		return coin;
	}
}
