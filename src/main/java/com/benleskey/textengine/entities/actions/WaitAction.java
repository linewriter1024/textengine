package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ActorActionSystem;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

/**
 * Action for waiting (passing time without doing anything).
 * For players, this advances world time.
 * For NPCs, this is a no-op action (idle).
 */
public class WaitAction extends Action {
	
	// Command and message constants
	public static final String CMD_WAIT = "wait";
	public static final String BROADCAST_WAITS = "actor_waits";
	public static final String M_ACTOR_ID = "actor_id";
	public static final String M_ACTOR_NAME = "actor_name";
	public static final String M_DURATION = "duration";
	
	public WaitAction(Game game, Actor actor, Entity unused, DTime timeRequired) {
		super(game, actor, unused, timeRequired);
	}
	
	@Override
	public UniqueType getActionType() {
		return game.getSystem(ActorActionSystem.class).ACTION_WAIT;
	}
	
	@Override
	public ActionValidation canExecute() {
		// Waiting always succeeds
		return ActionValidation.success();
	}
	
	@Override
	public CommandOutput execute() {
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		
		String actorDesc = eds.getActorDescription(actor, ws.getCurrentTime());
		String durationDesc = getDurationDescription();
		
		// Broadcast to all entities including the actor
		CommandOutput broadcast = CommandOutput.make(BROADCAST_WAITS)
			.put(M_ACTOR_ID, actor.getKeyId())
			.put(M_ACTOR_NAME, actorDesc)
			.put(M_DURATION, timeRequired.toMilliseconds())
			.text(Markup.concat(
				Markup.escape(capitalize(actorDesc)),
				Markup.raw(" waits for "),
				Markup.escape(durationDesc),
				Markup.raw(".")
			));
		
		bs.broadcast(actor, broadcast);
		return broadcast;
	}
	
	@Override
	public String getDescription() {
		return "waiting for " + getDurationDescription();
	}
	
	private String getDurationDescription() {
		long seconds = timeRequired.toMilliseconds() / 1000;
		if (seconds == 1) {
			return "1 second";
		} else if (seconds < 60) {
			return seconds + " seconds";
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			return minutes + (minutes == 1 ? " minute" : " minutes");
		} else {
			long hours = seconds / 3600;
			return hours + (hours == 1 ? " hour" : " hours");
		}
	}
	
	private String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
