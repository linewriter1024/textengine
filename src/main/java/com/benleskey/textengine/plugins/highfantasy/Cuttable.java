package com.benleskey.textengine.plugins.highfantasy;

import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.model.Entity;

/**
 * Interface for entities that can be cut down (e.g., trees).
 * This defines what happens when the entity is cut, not the cutting logic itself.
 * High fantasy specific - not all games have cutting.
 */
public interface Cuttable {
	/**
	 * Called when this entity is cut with a cutting tool.
	 * The entity should handle its own destruction, spawn items, etc.
	 * 
	 * @param actor The actor doing the cutting
	 * @param tool The tool being used (has TAG_CUT)
	 * @param toolName Human-readable name of the tool
	 * @param targetName Human-readable name of this entity
	 * @return CommandOutput describing what happened, or null to use default message
	 */
	CommandOutput onCut(Entity actor, Entity tool, String toolName, String targetName);
}
