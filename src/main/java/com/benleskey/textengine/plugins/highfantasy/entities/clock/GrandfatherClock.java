package com.benleskey.textengine.plugins.highfantasy.entities.clock;

import java.util.Random;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Acting;
import com.benleskey.textengine.entities.DynamicDescription;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.plugins.highfantasy.GameCalendar;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.EventSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.UniqueTypeSystem;
import com.benleskey.textengine.systems.WorldSystem;

/**
 * A grandfather clock that chimes on the hour.
 * Too heavy to move (100kg).
 * 
 * Uses action-based timing instead of onActionReady():
 * 1. On creation, queues a ClockWaitAction until the next chime time
 * 2. When wait completes, queues N ChimeActions (one per BONG)
 * 3. After last chime, queues the next ClockWaitAction
 * 
 * This allows precise timing: chimes start N seconds before the hour
 * so the last BONG occurs right as the hour strikes.
 */
public class GrandfatherClock extends Item implements Acting, DynamicDescription {

	// Clock action types
	public static UniqueType ACTION_CHIME;
	public static UniqueType ACTION_CLOCK_WAIT;

	// Chime action properties
	public static UniqueType PROP_CHIME_NUMBER;
	public static UniqueType PROP_TOTAL_CHIMES;

	public GrandfatherClock(long id, Game game) {
		super(id, game);
	}

	/**
	 * Register all clock-related types and actions.
	 * Called from HighFantasyPlugin.onCoreSystemsReady().
	 */
	public static void registerTypes(Game game) {
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
		ActionSystem aas = game.getSystem(ActionSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);

		// Initialize action types
		ACTION_CHIME = uts.getType("action_clock_chime");
		ACTION_CLOCK_WAIT = uts.getType("action_clock_wait");

		// Initialize chime properties
		PROP_CHIME_NUMBER = uts.getType("clock_chime_prop_number");
		PROP_TOTAL_CHIMES = uts.getType("clock_chime_prop_total");

		// Register action types
		aas.registerActionType(ACTION_CHIME, ChimeAction.class);
		aas.registerActionType(ACTION_CLOCK_WAIT, ClockWaitAction.class);

		// Register entity type
		es.registerEntityType(GrandfatherClock.class);
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

		// Clock is NOT takeable (too heavy to move)
		// Don't add TAG_TAKEABLE - this makes it immovable
		is.addTag(clock, is.TAG_WEIGHT, 100000L); // 100kg for reference
		es.addTag(clock, aas.TAG_ACTING); // Can perform actions (chiming)

		// Queue the first wait action
		clock.queueNextWait();

		return clock;
	}

	@Override
	public DTime getActionInterval() {
		return DTime.fromSeconds(60);
	}

	@Override
	public void onActionReady() {
	}

	/**
	 * Called when a ClockWaitAction completes.
	 * Queues the chime sequence for the current hour.
	 */
	public void onWaitComplete() {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ActionSystem aas = game.getSystem(ActionSystem.class);
		EventSystem evs = game.getSystem(EventSystem.class);

		DTime currentTime = ws.getCurrentTime();
		GameCalendar.CalendarDate date = GameCalendar.fromDTime(currentTime);

		// We're now at the start of the chiming sequence (N seconds before the hour)
		// The upcoming hour is the one we're chiming for
		int upcomingHour = (date.hour() + (date.minute() == 59 ? 1 : 0)) % 24;
		int chimeCount = upcomingHour % 12;
		if (chimeCount == 0)
			chimeCount = 12;

		log.log("Wait complete at %s, queueing %d chimes for hour %d",
				GameCalendar.formatTime(currentTime), chimeCount, upcomingHour);

		// Queue N chime actions, each taking 1 second
		for (int i = 1; i <= chimeCount; i++) {
			ChimeAction action = aas.add(ChimeAction.class, this, this, DTime.fromSeconds(1));
			action.setChimeNumber(i);
			action.setTotalChimes(chimeCount);
			evs.addEvent(aas.ACTION, currentTime, action);
		}
	}

	/**
	 * Queue a wait action until the next chime time.
	 * Called after the last chime or on clock creation.
	 */
	public void queueNextWait() {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ActionSystem aas = game.getSystem(ActionSystem.class);
		EventSystem evs = game.getSystem(EventSystem.class);

		DTime currentTime = ws.getCurrentTime();
		DTime waitDuration = calculateTimeUntilNextChime(currentTime);

		log.log("Queueing wait for %s (until next chime)", waitDuration);

		ClockWaitAction action = aas.add(ClockWaitAction.class, this, this, waitDuration);
		evs.addEvent(aas.ACTION, currentTime, action);
	}

	/**
	 * Calculate the time until the next chime sequence should start.
	 * Chimes start N seconds before the hour, where N is the hour number (1-12).
	 */
	private DTime calculateTimeUntilNextChime(DTime currentTime) {
		GameCalendar.CalendarDate date = GameCalendar.fromDTime(currentTime);

		// Calculate the next hour
		int nextHour = (date.hour() + 1) % 24;
		int chimeCount = nextHour % 12;
		if (chimeCount == 0)
			chimeCount = 12;

		// Calculate time until the next hour
		int secondsUntilNextHour = (59 - date.minute()) * 60 + (60 - date.second());
		if (date.millisecond() > 0) {
			// Partial second already passed
			secondsUntilNextHour--;
		}

		// Start chiming N seconds before the hour
		int secondsUntilChimeStart = secondsUntilNextHour - chimeCount;

		// If we're already past the chime start time for this hour, wait for the next
		// one
		if (secondsUntilChimeStart < 0) {
			// Wait until the next hour's chime
			nextHour = (nextHour + 1) % 24;
			chimeCount = nextHour % 12;
			if (chimeCount == 0)
				chimeCount = 12;
			secondsUntilChimeStart = secondsUntilNextHour + 3600 - chimeCount;
		}

		return DTime.fromSeconds(secondsUntilChimeStart);
	}

	@Override
	public String getDynamicDescription() {
		var ws = game.getSystem(WorldSystem.class);
		return "The clock shows: " + GameCalendar.formatFull(ws.getCurrentTime());
	}
}
