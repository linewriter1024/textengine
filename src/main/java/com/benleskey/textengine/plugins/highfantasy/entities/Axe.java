package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

/**
 * An axe that can cut down cuttable things (trees).
 * The axe has TAG_CUT and TAG_TOOL tags.
 * The generic tag system handles the cutting interaction.
 */
public class Axe extends Item {
	
	public Axe(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create an axe with the specified description.
	 * Adds basic look, TAG_CUT, and TAG_TOOL automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the axe (e.g., "a rusty axe")
	 * @return The created and configured axe entity
	 */
	public static Axe create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		Axe axe = es.add(Axe.class);
		ls.addLook(axe, "basic", description);
		is.addTag(axe, is.TAG_CUT);
		is.addTag(axe, is.TAG_TOOL);
		
		return axe;
	}
}
