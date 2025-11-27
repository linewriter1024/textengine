package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * An ancient coin from a lost civilization.
 */
public class AncientCoin extends Item {
	
	public AncientCoin(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create an ancient coin with the specified description.
	 * Adds basic look and TAG_CURRENCY automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the coin (e.g., "an ancient coin")
	 * @return The created and configured coin entity
	 */
	public static AncientCoin create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		AncientCoin coin = es.add(AncientCoin.class);
		ls.addLook(coin, "basic", description);
		is.addTag(coin, is.TAG_CURRENCY);
		is.addTag(coin, is.TAG_TAKEABLE);
		is.addTag(coin, is.TAG_WEIGHT, 10); // 10g
		
		return coin;
	}
}
