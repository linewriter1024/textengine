package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * A rusty old sword, still potentially usable as a weapon.
 */
public class RustySword extends Item {
	
	public RustySword(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a rusty sword with the specified description.
	 * Adds basic look and TAG_WEAPON automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the sword (e.g., "a rusty sword")
	 * @return The created and configured sword entity
	 */
	public static RustySword create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		RustySword sword = es.add(RustySword.class);
		ls.addLook(sword, "basic", description);
		is.addTag(sword, is.TAG_WEAPON);
		
		return sword;
	}
}
