package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;
import com.benleskey.textengine.systems.ActorActionSystem;

/**
 * Action for waiting (passing time without doing anything).
 * For players, this advances world time.
 * For NPCs, this is a no-op action (idle).
 */
public class WaitAction extends Action {
	
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
	public boolean execute() {
		// No-op - time advancement happens in queueAction for players
		// For NPCs, this is just an idle action
		return true;
	}
	
	@Override
	public String getDescription() {
		long seconds = timeRequired.toMilliseconds() / 1000;
		if (seconds == 1) {
			return "waiting for 1 second";
		} else if (seconds < 60) {
			return "waiting for " + seconds + " seconds";
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			return "waiting for " + minutes + (minutes == 1 ? " minute" : " minutes");
		} else {
			long hours = seconds / 3600;
			return "waiting for " + hours + (hours == 1 ? " hour" : " hours");
		}
	}
}
