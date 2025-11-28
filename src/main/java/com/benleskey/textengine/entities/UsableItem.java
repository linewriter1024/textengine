package com.benleskey.textengine.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Entity;

/**
 * Interface for items that can be used by themselves (without a target).
 */
public interface UsableItem {
	/**
	 * Handle using this item without a target.
	 * 
	 * @param client The client using the item
	 * @param actor  The actor using the item
	 * @return CommandOutput describing the result, or null if this item has no solo
	 *         use
	 */
	CommandOutput useSolo(Client client, Entity actor);
}
