package com.benleskey.textengine.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * Represents an item in the game world.
 * Items can be picked up, dropped, examined, and contained within places or actors.
 * Examples: stones, grass, swords, containers, etc.
 */
public class Item extends Entity {

	public Item(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a new item with a basic description.
	 * 
	 * @param game The game instance
	 * @param description Basic description of the item (e.g., "a stone", "some grass")
	 * @return The newly created item
	 */
	public static Item create(Game game, String description) throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		Item item = es.add(Item.class);
		ls.addLook(item, "basic", description);
		return item;
	}
}
