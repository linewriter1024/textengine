package com.benleskey.textengine.entities;

import com.benleskey.textengine.model.UniqueType;

import java.util.List;

/**
 * Interface for entities that provide their own required tags.
 * This allows entities to declare what tags they should always have,
 * making them self-describing and reducing coupling with ItemTemplateSystem.
 */
public interface TagProvider {
	/**
	 * Returns the list of tags that this entity should have.
	 * Called after entity creation to apply entity-specific tags.
	 * 
	 * @return List of UniqueType tags, or empty list if no tags
	 */
	List<UniqueType> getRequiredTags();
}
