package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.DynamicDescription;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.plugins.highfantasy.HighFantasyPlugin;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.WorldSystem;

import java.util.Random;

/**
 * A grandfather clock that chimes on the hour.
 * Too heavy to move (100kg).
 * Implements Acting to queue ChimeActions when the hour changes.
 */
public class GrandfatherClock extends Item implements Acting, DynamicDescription {

	public GrandfatherClock(long id, Game game) {
		super(id, game);
	}

	/**
	 * Create a grandfather clock with proper tags and weight.
	 */
	public static GrandfatherClock create(Game game, Random random) {
		var es = game.getSystem(EntitySystem.class);
		var ls = game.getSystem(LookSystem.class);
		var is = game.getSystem(ItemSystem.class);
		var aas = game.getSystem(ActionSystem.class);

		GrandfatherClock clock = es.add(GrandfatherClock.class);
		ls.addLook(clock, ls.LOOK_BASIC, "a grandfather clock");

		// Clock is too heavy to take
		is.addTag(clock, is.TAG_TAKEABLE);
		is.addTag(clock, is.TAG_WEIGHT, 100000L); // 100kg - too heavy to carry
		es.addTag(clock, aas.TAG_ACTING); // Can perform actions (chiming)

		return clock;
	}

	@Override
	public DTime getActionInterval() {
		// Check every minute for hour changes
		return DTime.fromSeconds(60);
	}

	@Override
	public void onActionReady() {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ActionSystem aas = game.getSystem(ActionSystem.class);

		DTime currentTime = ws.getCurrentTime();
		GameCalendar.CalendarDate date = GameCalendar.fromDTime(currentTime);
		int currentMinute = date.minute();
		int currentHour = date.hour();

		// Chime on the hour (minute 0) - tick interval guarantees once per minute
		if (currentMinute == 0) {
			log.log("Queueing chime action for hour %d", currentHour);
			// Queue an instant chime action (zero delay)
			aas.queueAction(this, HighFantasyPlugin.ACTION_CHIME, this, DTime.ZERO);
		}
	}

	@Override
	public String getDynamicDescription() {
		var ws = game.getSystem(WorldSystem.class);
		return "The clock shows: " + GameCalendar.formatFull(ws.getCurrentTime());
	}
}
