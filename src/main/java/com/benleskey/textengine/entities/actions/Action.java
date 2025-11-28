package com.benleskey.textengine.entities.actions;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.UniqueType;

/**
 * Base class for actions that actors (players and NPCs) can perform.
 * Actions are queued through ActorActionSystem and executed when ready.
 * 
 * Each action type must:
 * - Define an action type identifier (UniqueType)
 * - Implement execute() to perform the action
 * - Implement getDescription() for visibility to other entities
 * 
 * Action instances are created per execution, not stored in database.
 * The database stores action type ID + target entity ID.
 */
public abstract class Action {
	protected final Game game;
	protected final Actor actor;
	protected final Entity target;
	protected final DTime timeRequired;
	
	/**
	 * Create an action instance.
	 * 
	 * @param game The game instance
	 * @param actor The actor performing the action
	 * @param target The target entity (destination for move, item for take/drop, etc.)
	 * @param timeRequired How long this action takes to complete
	 */
	public Action(Game game, Actor actor, Entity target, DTime timeRequired) {
		this.game = game;
		this.actor = actor;
		this.target = target;
		this.timeRequired = timeRequired;
	}
	
	/**
	 * Get the unique type identifying this action class.
	 * Used to store/retrieve action type from database.
	 * 
	 * @return The action type
	 */
	public abstract UniqueType getActionType();
	
	/**
	 * Execute this action.
	 * Called when the action's time requirement is met.
	 * Should return CommandOutput that will be broadcast to the actor and nearby entities.
	 * 
	 * @return CommandOutput to broadcast (both to actor and nearby entities), or null if action failed
	 */
	public abstract CommandOutput execute();
	
	/**
	 * Check if this action can be executed.
	 * Called before queueing and before execution to validate preconditions.
	 * Examples: check if item is takeable, not too heavy, target still exists, etc.
	 * 
	 * @return ActionValidation result indicating if action can execute and why not if it can't
	 */
	public abstract ActionValidation canExecute();
	
	/**
	 * Get a human-readable description of this action for observers.
	 * Used when other entities look at the actor performing this action.
	 * Example: "moving north", "taking a sword", "dropping a shield"
	 * 
	 * @return Description of what the actor is doing
	 */
	public abstract String getDescription();
	
	/**
	 * Get the time required for this action to complete.
	 * 
	 * @return Time required
	 */
	public DTime getTimeRequired() {
		return timeRequired;
	}
	
	/**
	 * Get the target entity for this action.
	 * 
	 * @return The target entity
	 */
	public Entity getTarget() {
		return target;
	}
	
	/**
	 * Get the actor performing this action.
	 * 
	 * @return The actor
	 */
	public Actor getActor() {
		return actor;
	}
}
