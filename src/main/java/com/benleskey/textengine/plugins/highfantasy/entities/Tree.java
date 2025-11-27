package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;

/**
 * A tree that can be cut down with an axe to produce wood.
 * Trees are entities in the world that can be interacted with.
 */
public class Tree extends Item {
	
	public Tree(long id, Game game) {
		super(id, game);
	}
}
