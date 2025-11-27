package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.DynamicDescription;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;

import java.util.Random;

/**
 * A pocket timepiece that shows the current time.
 * Can be examined to see the current game time.
 */
public class Timepiece extends Item implements DynamicDescription {
	
	public Timepiece(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a timepiece with proper tags and weight.
	 */
	public static Timepiece create(Game game, Random random) {
		var es = game.getSystem(com.benleskey.textengine.systems.EntitySystem.class);
		var ls = game.getSystem(LookSystem.class);
		var is = game.getSystem(ItemSystem.class);
		
		Timepiece timepiece = es.add(Timepiece.class);
		ls.addLook(timepiece, "basic", "a pocket timepiece");
		
		// Timepiece is takeable and lightweight
		is.addTag(timepiece, is.TAG_TAKEABLE);
		is.addTag(timepiece, is.TAG_WEIGHT, 100L); // 100g
		
		return timepiece;
	}
	
	/**
	 * Get the current time displayed on this timepiece.
	 */
	public String getTimeDisplay() {
		var ws = game.getSystem(com.benleskey.textengine.systems.WorldSystem.class);
		return GameCalendar.formatFull(ws.getCurrentTime());
	}
	
	@Override
	public String getDynamicDescription() {
		return "The timepiece shows: " + getTimeDisplay();
	}
}
