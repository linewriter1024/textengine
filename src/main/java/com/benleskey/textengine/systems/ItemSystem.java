package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.Reference;
import com.benleskey.textengine.model.UniqueType;

/**
 * System for managing item tags using the EntityTagSystem.
 * Items are non-stackable and use UniqueType tags to define their properties.
 * 
 * Common tags:
 * - "tool" - Item can be used as a tool
 * - "container" - Item can contain other items
 * - "cuttable" - Item can be cut down (trees)
 * - "toy" - Item is a toy (makes sound, etc.)
 */
public class ItemSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	private EntityTagSystem tagSystem;
	private UniqueTypeSystem typeSystem;
	
	// Common item tags (initialized in onSystemInitialize)
	public UniqueType TAG_TOOL;
	public UniqueType TAG_CONTAINER;
	public UniqueType TAG_CUTTABLE;
	public UniqueType TAG_TOY;
	public UniqueType TAG_CUT;  // For tools that can cut things
	public UniqueType TAG_WEIGHT;  // Numeric tag for item weight
	public UniqueType TAG_INFINITE_RESOURCE;  // For infinite resources (grass, water, etc.)
	public UniqueType TAG_TAKEABLE;  // Item can be taken (picked up)
	public UniqueType TAG_CARRY_WEIGHT;  // Maximum weight entity can carry (in grams)
	public UniqueType TAG_OPEN;  // Container is open (value: 1=open, 0 or absent=closed)

	public ItemSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		tagSystem = game.getSystem(EntityTagSystem.class);
		typeSystem = game.getSystem(UniqueTypeSystem.class);
		
		// Initialize common item tags
		TAG_TOOL = typeSystem.getType("item_tag_tool");
		TAG_CONTAINER = typeSystem.getType("item_tag_container");
		TAG_CUTTABLE = typeSystem.getType("item_tag_cuttable");
		TAG_TOY = typeSystem.getType("item_tag_toy");
		TAG_CUT = typeSystem.getType("item_tag_cut");
		TAG_WEIGHT = typeSystem.getType("item_tag_weight");
		TAG_INFINITE_RESOURCE = typeSystem.getType("item_tag_infinite_resource");
		TAG_TAKEABLE = typeSystem.getType("item_tag_takeable");
		TAG_CARRY_WEIGHT = typeSystem.getType("item_tag_carry_weight");
		TAG_OPEN = typeSystem.getType("item_tag_open");
	}

	/**
	 * Add a tag to an item.
	 */
	public Reference addTag(Entity item, UniqueType tag) {
		return tagSystem.addTag(item, tag);
	}

	/**
	 * Add a tag with a numeric value to an item.
	 */
	public Reference addTag(Entity item, UniqueType tag, long value) {
		return tagSystem.addTag(item, tag, value);
	}

	/**
	 * Get the numeric value of a tag on an item.
	 */
	public Long getTagValue(Entity item, UniqueType tag, DTime when) {
		return tagSystem.getTagValue(item, tag, when);
	}

	/**
	 * Remove a tag from an item at a specific time.
	 */
	public void removeTag(Entity item, UniqueType tag, DTime when) {
		tagSystem.removeTag(item, tag, when);
	}

	/**
	 * Check if an item has a specific tag.
	 */
	public boolean hasTag(Entity item, UniqueType tag, DTime when) {
		return tagSystem.hasTag(item, tag, when);
	}
	
	/**
	 * Update a tag value (cancels old value and adds new one).
	 */
	public Reference updateTagValue(Entity item, UniqueType tag, long newValue, DTime when) {
		return tagSystem.updateTagValue(item, tag, newValue, when);
	}
}
