package com.benleskey.textengine.entities;

import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;

/**
 * Interface for entities that can decide what actions to perform.
 * Extends Entity so Acting can be used as a type constraint for actions.
 * Entities implementing this interface should be tagged with TAG_ACTING.
 * The ActionSystem calls onActionReady when the actor has no pending action.
 * 
 * The entity should queue an action using ActionSystem.queueAction().
 * The ActionSystem handles execution, time tracking, and broadcasts.
 */
public interface Acting extends Entity {

	/**
	 * Called when this actor has no pending action and should decide what to do
	 * next.
	 * The implementation should call ActionSystem.queueAction() to queue an
	 * action.
	 * Use game.getSystem(WorldSystem.class).getCurrentTime() to get current time.
	 */
	void onActionReady();

	/**
	 * How often to check if this actor is ready for a new action.
	 * 
	 * @return The interval between action readiness checks
	 */
	DTime getActionInterval();
}
