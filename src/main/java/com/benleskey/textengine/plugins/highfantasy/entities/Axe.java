package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.TagProvider;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ItemSystem;

import java.util.List;

/**
 * An axe that can cut down cuttable things (trees).
 * The axe itself declares TAG_CUT and TAG_TOOL tags via TagProvider.
 * The generic tag system handles the cutting interaction.
 */
public class Axe extends Item implements TagProvider {
	
	public Axe(long id, Game game) {
		super(id, game);
	}
	
	@Override
	public List<UniqueType> getRequiredTags() {
		ItemSystem is = game.getSystem(ItemSystem.class);
		return List.of(is.TAG_TOOL, is.TAG_CUT);
	}
}
