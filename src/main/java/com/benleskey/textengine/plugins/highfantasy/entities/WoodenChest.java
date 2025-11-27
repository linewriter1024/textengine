package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.TagProvider;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ItemSystem;

import java.util.List;

/**
 * A wooden chest that can contain other items.
 */
public class WoodenChest extends Item implements TagProvider {
	
	public WoodenChest(long id, Game game) {
		super(id, game);
	}
	
	@Override
	public List<UniqueType> getRequiredTags() {
		ItemSystem is = game.getSystem(ItemSystem.class);
		return List.of(is.TAG_CONTAINER);
	}
}
