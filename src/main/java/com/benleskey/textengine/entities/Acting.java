package com.benleskey.textengine.entities;

import com.benleskey.textengine.model.DTime;

/**
 * Interface for entities that can decide what actions to perform.
 * Entities implementing this interface should be tagged with TAG_ACTING.
 * The ActorActionSystem calls onActionReady when the actor has no pending action.
 * 
 * The entity should queue an action using ActorActionSystem.queueAction().
 * The ActorActionSystem handles execution, time tracking, and broadcasts.
 */
public interface Acting {
	
	/**
	 * Called when this actor has no pending action and should decide what to do next.
	 * The implementation should call ActorActionSystem.queueAction() to queue an action.
	 * 
	 * @param currentTime The current game time
	 */
	void onActionReady(DTime currentTime);
	
	/**
	 * How often to check if this actor is ready for a new action.
	 * Similar to Tickable.getTickInterval().
	 * 
	 * @return The interval between action readiness checks
	 */
	DTime getActionInterval();
}
