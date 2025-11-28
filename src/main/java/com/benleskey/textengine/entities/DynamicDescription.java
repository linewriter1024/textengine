package com.benleskey.textengine.entities;

/**
 * Interface for entities that can provide dynamic descriptions.
 * This allows entities to generate contextual information when examined.
 * 
 * For example, a clock can show the current time, a thermometer can show
 * temperature,
 * or a book can show different text based on which page is open.
 */
public interface DynamicDescription {
	/**
	 * Get the dynamic description for this entity.
	 * This is called when the entity is examined and is shown after the basic
	 * description.
	 * 
	 * @return The dynamic description text, or null if no dynamic description is
	 *         available
	 */
	String getDynamicDescription();
}
