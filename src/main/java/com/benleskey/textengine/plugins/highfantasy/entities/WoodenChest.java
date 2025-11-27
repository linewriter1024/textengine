package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * A wooden chest that can contain other items.
 */
public class WoodenChest extends Item {
	
	public WoodenChest(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a wooden chest with the specified description.
	 * Adds basic look and TAG_CONTAINER automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the chest (e.g., "a wooden chest")
	 * @return The created and configured chest entity
	 */
	public static WoodenChest create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		WoodenChest chest = es.add(WoodenChest.class);
		ls.addLook(chest, "basic", description);
		is.addTag(chest, is.TAG_CONTAINER);
		is.addTag(chest, is.TAG_TAKEABLE);
		is.addTag(chest, is.TAG_WEIGHT, 5000); // 5kg
		
		return chest;
	}
}
