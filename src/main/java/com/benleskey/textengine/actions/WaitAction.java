package com.benleskey.textengine.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.Action;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ActorActionSystem;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.EntitySystem;
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
	// Note: EntitySystem.M_ACTOR_ID, EntitySystem.M_ACTOR_NAME defined in
	// EntitySystem

	// Note: WorldSystem.M_DURATION defined in WorldSystem

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
				.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
				.put(EntitySystem.M_ACTOR_NAME, actorDesc)
				.put(WorldSystem.M_DURATION, timeRequired.toMilliseconds())
				.text(Markup.concat(
						Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
						Markup.raw(" "),
						Markup.verb("wait"),
						Markup.raw(" for "),
						Markup.escape(durationDesc),
						Markup.raw(".")));

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
}
