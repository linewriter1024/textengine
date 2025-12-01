package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.DynamicDescription;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.plugins.highfantasy.HighFantasyPlugin;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.systems.WorldSystem;

import java.util.Random;

/**
 * A grandfather clock that chimes on the hour.
 * Too heavy to move (100kg).
 * Implements Acting to queue ChimeActions when the hour changes.
 */
public class GrandfatherClock extends Item implements Acting, DynamicDescription {

	// Tag to track last hour chimed (prevents multiple chimes in same hour)
	private static UniqueType TAG_LAST_CHIME_HOUR;

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
		var uts = game.getSystem(UniqueTypeSystem.class);

		// Initialize tag if needed
		if (TAG_LAST_CHIME_HOUR == null) {
			TAG_LAST_CHIME_HOUR = uts.getType("clock_last_chime_hour");
		}

		GrandfatherClock clock = es.add(GrandfatherClock.class);
		ls.addLook(clock, ls.LOOK_BASIC, "a grandfather clock");

		// Clock is too heavy to take
		is.addTag(clock, is.TAG_TAKEABLE);
		is.addTag(clock, is.TAG_WEIGHT, 100000L); // 100kg - too heavy to carry
		es.addTag(clock, aas.TAG_ACTING); // Can perform actions (chiming)
		// Initialize last chime hour to -1 (never chimed)
		es.addTag(clock, TAG_LAST_CHIME_HOUR, -1L);

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
		EntitySystem es = game.getSystem(EntitySystem.class);
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);

		// Ensure tag is initialized
		if (TAG_LAST_CHIME_HOUR == null) {
			TAG_LAST_CHIME_HOUR = uts.getType("clock_last_chime_hour");
		}

		DTime currentTime = ws.getCurrentTime();
		GameCalendar.CalendarDate date = GameCalendar.fromDTime(currentTime);
		int currentMinute = date.minute();
		int currentHour = date.hour();

		// Get total hours since epoch for unique hour tracking
		long totalHours = currentTime.toMilliseconds() / (1000 * 60 * 60);

		// Check if we already chimed this hour
		Long lastChimeHour = es.getTagValue(this, TAG_LAST_CHIME_HOUR, currentTime);

		// Only chime on the hour (minute 0) and if we haven't chimed this hour yet
		if (currentMinute == 0 && (lastChimeHour == null || lastChimeHour != totalHours)) {
			log.log("Queueing chime action for hour %d (total hour %d)", currentHour, totalHours);
			// Mark that we've chimed this hour
			es.updateTagValue(this, TAG_LAST_CHIME_HOUR, totalHours, currentTime);
			// Queue a chime action that takes 1 second
			aas.queueAction(this, HighFantasyPlugin.ACTION_CHIME, this, DTime.fromSeconds(1));
		}
	}

	@Override
	public String getDynamicDescription() {
		var ws = game.getSystem(WorldSystem.class);
		return "The clock shows: " + GameCalendar.formatFull(ws.getCurrentTime());
	}
}
