package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.actions.*;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * ActorActionSystem manages action types and execution for all actors (players and NPCs).
 * 
 * Action System Design:
 * - Action types are registered like entity types (registry pattern)
 * - Pending actions stored in database with action type ID + target entity ID
 * - Action classes define execution logic and descriptions
 * - Players see instant execution (time auto-advanced), NPCs queue actions
 * 
 * Action Visibility:
 * - Pending actions visible to other players/NPCs via getPendingActionDescription()
 * - Used by look command to show "a goblin is taking a sword"
 */
public class ActorActionSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Action type registry (maps UniqueType -> Action class)
	private final Map<UniqueType, Class<? extends Action>> actionTypes = new HashMap<>();
	
	// Action type constants (initialized in onSystemInitialize)
	public UniqueType ACTION_MOVE;
	public UniqueType ACTION_ITEM_TAKE;
	public UniqueType ACTION_ITEM_DROP;
	
	public ActorActionSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() throws DatabaseException {
		UniqueTypeSystem uts = game.getSystem(UniqueTypeSystem.class);
		
		// Define action types
		ACTION_MOVE = uts.getType("action_move");
		ACTION_ITEM_TAKE = uts.getType("action_item_take");
		ACTION_ITEM_DROP = uts.getType("action_item_drop");
		
		// Register action classes
		registerActionType(ACTION_MOVE, MoveAction.class);
		registerActionType(ACTION_ITEM_TAKE, TakeItemAction.class);
		registerActionType(ACTION_ITEM_DROP, DropItemAction.class);
	}
	
	/**
	 * Register an action type with its implementation class.
	 * Similar to EntitySystem.registerEntityType().
	 * 
	 * @param actionType The unique type identifying this action
	 * @param actionClass The class implementing this action
	 */
	public void registerActionType(UniqueType actionType, Class<? extends Action> actionClass) {
		actionTypes.put(actionType, actionClass);
		log.log("Registered action type %s to class %s", actionType, actionClass.getCanonicalName());
	}
	
	/**
	 * Create an action instance from database-stored data.
	 * 
	 * @param actionType The type of action to create
	 * @param actor The actor performing the action
	 * @param target The target entity
	 * @param timeRequired How long the action takes
	 * @return The action instance, or null if type not registered
	 */
	public Action createAction(UniqueType actionType, Actor actor, Entity target, DTime timeRequired) {
		Class<? extends Action> actionClass = actionTypes.get(actionType);
		if (actionClass == null) {
			log.log("Unknown action type: %s", actionType);
			return null;
		}
		
		try {
			Constructor<? extends Action> constructor = actionClass.getDeclaredConstructor(
				Game.class, Actor.class, Entity.class, DTime.class);
			return constructor.newInstance(game, actor, target, timeRequired);
		} catch (Exception e) {
			log.log("Failed to create action of type %s: %s", actionType, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Queue an action for an actor.
	 * Delegates to PendingActionSystem which handles player vs NPC differences.
	 * 
	 * @param actor The actor performing the action
	 * @param actionType The type of action
	 * @param target The target entity
	 * @param timeRequired How long the action takes
	 */
	public void queueAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		PendingActionSystem pas = game.getSystem(PendingActionSystem.class);
		pas.queueAction(actor, actionType, timeRequired, target);
	}
	
	/**
	 * Execute a pending action.
	 * Creates the action instance and calls its execute() method.
	 * 
	 * @param actor The actor performing the action
	 * @param actionType The type of action
	 * @param target The target entity
	 * @param timeRequired How long the action took
	 * @return true if action succeeded, false if it failed
	 */
	public boolean executeAction(Actor actor, UniqueType actionType, Entity target, DTime timeRequired) {
		Action action = createAction(actionType, actor, target, timeRequired);
		if (action == null) {
			return false;
		}
		
		boolean success = action.execute();
		log.log("Actor %d executed %s action: %s", actor.getId(), actionType, success ? "success" : "failed");
		return success;
	}
	
	/**
	 * Get a description of an actor's pending action for observers.
	 * Used by look command to show what NPCs are doing.
	 * Example: "a goblin is taking a sword"
	 * 
	 * @param actor The actor with a pending action
	 * @return Description of pending action, or null if no action pending
	 */
	public String getPendingActionDescription(Actor actor) {
		PendingActionSystem pas = game.getSystem(PendingActionSystem.class);
		PendingActionSystem.PendingAction pending = pas.getPendingAction(actor);
		
		if (pending == null) {
			return null;
		}
		
		EntitySystem es = game.getSystem(EntitySystem.class);
		Entity target = es.get(pending.targetEntityId);
		
		Action action = createAction(pending.type, actor, target, pending.timeRequired);
		if (action == null) {
			return null;
		}
		
		return action.getDescription();
	}
	

}
