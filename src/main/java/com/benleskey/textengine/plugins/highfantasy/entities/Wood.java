package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * Generic wood item that can be customized with different descriptions.
 * Covers: fallen branches, driftwood, twisted roots, etc.
 */
public class Wood extends Item {
	
	public Wood(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a wood item with the specified description.
	 * Adds basic look automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the wood (e.g., "a fallen branch", "a piece of driftwood")
	 * @return The created and configured wood entity
	 */
	public static Wood create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		Wood wood = es.add(Wood.class);
		ls.addLook(wood, "basic", description);
		is.addTag(wood, is.TAG_TAKEABLE);
		is.addTag(wood, is.TAG_WEIGHT, 300); // 300g
		
		return wood;
	}
}
