package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * A weathered scroll with ancient writings.
 */
public class WeatheredScroll extends Item {
	
	public WeatheredScroll(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a weathered scroll with the specified description.
	 * Adds basic look and TAG_READABLE automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the scroll (e.g., "a weathered scroll")
	 * @return The created and configured scroll entity
	 */
	public static WeatheredScroll create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		WeatheredScroll scroll = es.add(WeatheredScroll.class);
		ls.addLook(scroll, "basic", description);
		is.addTag(scroll, is.TAG_READABLE);
		
		return scroll;
	}
}
