package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.DynamicDescription;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Tickable;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.util.Markup;

/**
 * A grandfather clock that ticks every minute and chimes on the hour.
 * Too heavy to move (100kg).
 */
public class GrandfatherClock extends Item implements Tickable, DynamicDescription {
	
	private int lastChimeHour = -1;
	
	public GrandfatherClock(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a grandfather clock with proper tags and weight.
	 */
	public static GrandfatherClock create(Game game, String description) {
		var es = game.getSystem(com.benleskey.textengine.systems.EntitySystem.class);
		var ls = game.getSystem(LookSystem.class);
		var is = game.getSystem(ItemSystem.class);
		
		GrandfatherClock clock = es.add(GrandfatherClock.class);
		ls.addLook(clock, "basic", description);
		
		// Clock is too heavy to take
		is.addTag(clock, is.TAG_TAKEABLE);
		is.addTag(clock, is.TAG_WEIGHT, 100000L); // 100kg - too heavy to carry
		is.addTag(clock, is.TAG_TICKABLE); // Receives tick updates
		
		return clock;
	}
	
	@Override
	public DTime getTickInterval() {
		// Tick every minute
		return DTime.fromSeconds(60);
	}
	
	@Override
	public void onTick(DTime currentTime, DTime timeSinceLastTick) {
		GameCalendar.CalendarDate date = GameCalendar.fromDTime(currentTime);
		int currentHour = date.hour();
		int currentMinute = date.minute();
		
		// Only chime on the hour (minute 0)
		if (currentMinute == 0 && currentHour != lastChimeHour) {
			// Chime on the hour
			int chimeCount = currentHour % 12;
			if (chimeCount == 0) chimeCount = 12;
			
			String chimes = "BONG ".repeat(chimeCount).trim();
			CommandOutput chimeOutput = CommandOutput.make("clock_chime")
				.put("success", true)
				.put("entity_id", String.valueOf(getId()))
				.text(Markup.escape(String.format("The grandfather clock chimes %d %s. %s", 
					chimeCount, 
					chimeCount == 1 ? "time" : "times",
					chimes)));
			
			// Broadcast to all nearby entities using BroadcastSystem
			game.getSystem(com.benleskey.textengine.systems.BroadcastSystem.class).broadcast(this, chimeOutput);
			
			lastChimeHour = currentHour;
		}
		// No output for regular ticks - clock ticks silently
	}
	
	@Override
	public String getDynamicDescription() {
		var ws = game.getSystem(com.benleskey.textengine.systems.WorldSystem.class);
		return "The clock shows: " + GameCalendar.formatFull(ws.getCurrentTime());
	}
}
