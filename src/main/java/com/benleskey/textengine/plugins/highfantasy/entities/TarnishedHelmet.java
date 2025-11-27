package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * A tarnished helmet from an ancient warrior.
 */
public class TarnishedHelmet extends Item {
	
	public TarnishedHelmet(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a tarnished helmet with the specified description.
	 * Adds basic look and TAG_ARMOR automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the helmet (e.g., "a tarnished helmet")
	 * @return The created and configured helmet entity
	 */
	public static TarnishedHelmet create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		TarnishedHelmet helmet = es.add(TarnishedHelmet.class);
		ls.addLook(helmet, "basic", description);
		is.addTag(helmet, is.TAG_ARMOR);
		
		return helmet;
	}
}
