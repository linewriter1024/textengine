package com.benleskey.textengine.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Entity;

/**
 * Interface for items that can be used on a target.
 */
public interface UsableOnTarget {
	/**
	 * Handle using this item on a target.
	 * 
	 * @param client     The client using the item
	 * @param actor      The actor using the item
	 * @param target     The target entity
	 * @param targetName Human-readable name of the target
	 * @return CommandOutput describing the result, or null if this interaction is
	 *         not supported
	 */
	CommandOutput useOn(Client client, Entity actor, Entity target, String targetName);
}
