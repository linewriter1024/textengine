package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.exceptions.DatabaseException;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.Entity;

import java.util.Optional;

/**
 * System for managing item-specific properties.
 * Handles item types, quantities, weight, and other item attributes.
 */
public class ItemSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Item type property subsystem
	private PropertiesSubSystem<Long, String, String> itemTypeProperties;
	
	// Item quantity property subsystem (for stackable/infinite resources)
	private PropertiesSubSystem<Long, String, Long> itemQuantityProperties;
	
	// Item weight property subsystem
	private PropertiesSubSystem<Long, String, Long> itemWeightProperties;
	
	// Item container flag property subsystem
	private PropertiesSubSystem<Long, String, Long> itemContainerProperties;

	public ItemSystem(Game game) {
		super(game);
	}

	@Override
	public void onSystemInitialize() throws DatabaseException {
		// Initialize property subsystems for different item attributes
		// Note: We create these as standalone subsystems but don't register them with the game
		// They are managed by ItemSystem and initialized here
		itemTypeProperties = new PropertiesSubSystem<>(
			game, 
			"item_type",
			PropertiesSubSystem.longHandler(),
			PropertiesSubSystem.stringHandler(),
			PropertiesSubSystem.stringHandler()
		);
		itemTypeProperties.onSystemInitialize();
		
		itemQuantityProperties = new PropertiesSubSystem<>(
			game,
			"item_quantity", 
			PropertiesSubSystem.longHandler(),
			PropertiesSubSystem.stringHandler(),
			PropertiesSubSystem.longHandler()
		);
		itemQuantityProperties.onSystemInitialize();
		
		itemWeightProperties = new PropertiesSubSystem<>(
			game,
			"item_weight",
			PropertiesSubSystem.longHandler(),
			PropertiesSubSystem.stringHandler(),
			PropertiesSubSystem.longHandler()
		);
		itemWeightProperties.onSystemInitialize();
		
		itemContainerProperties = new PropertiesSubSystem<>(
			game,
			"item_container",
			PropertiesSubSystem.longHandler(),
			PropertiesSubSystem.stringHandler(),
			PropertiesSubSystem.longHandler()
		);
		itemContainerProperties.onSystemInitialize();
	}

	/**
	 * Set the type of an item (e.g., "RESOURCE", "EQUIPMENT", "CONTAINER").
	 */
	public void setItemType(Entity item, ItemType type) throws DatabaseException {
		itemTypeProperties.set(item.getId(), "type", type.name());
	}

	/**
	 * Get the type of an item.
	 */
	public Optional<ItemType> getItemType(Entity item) throws DatabaseException {
		return itemTypeProperties.get(item.getId(), "type")
			.map(ItemType::valueOf);
	}

	/**
	 * Set the quantity of an item. Use -1 for infinite resources.
	 */
	public void setQuantity(Entity item, long quantity) throws DatabaseException {
		itemQuantityProperties.set(item.getId(), "quantity", quantity);
	}

	/**
	 * Get the quantity of an item. Returns Optional.empty() if not set.
	 * A value of -1 indicates infinite quantity.
	 */
	public Optional<Long> getQuantity(Entity item) throws DatabaseException {
		return itemQuantityProperties.get(item.getId(), "quantity");
	}

	/**
	 * Check if an item has infinite quantity (e.g., grass, stones in a field).
	 */
	public boolean isInfinite(Entity item) throws DatabaseException {
		Optional<Long> quantity = getQuantity(item);
		return quantity.isPresent() && quantity.get() == -1;
	}

	/**
	 * Set the weight of an item (in arbitrary units).
	 */
	public void setWeight(Entity item, long weight) throws DatabaseException {
		itemWeightProperties.set(item.getId(), "weight", weight);
	}

	/**
	 * Get the weight of an item.
	 */
	public Optional<Long> getWeight(Entity item) throws DatabaseException {
		return itemWeightProperties.get(item.getId(), "weight");
	}

	/**
	 * Mark an item as a container (can hold other items).
	 */
	public void setIsContainer(Entity item, boolean isContainer) throws DatabaseException {
		itemContainerProperties.set(item.getId(), "container", isContainer ? 1L : 0L);
	}

	/**
	 * Check if an item is a container.
	 */
	public boolean isContainer(Entity item) throws DatabaseException {
		Optional<Long> containerFlag = itemContainerProperties.get(item.getId(), "container");
		return containerFlag.isPresent() && containerFlag.get() == 1L;
	}

	/**
	 * Enum for item types.
	 */
	public enum ItemType {
		RESOURCE,    // Natural resources like stones, grass, wood
		EQUIPMENT,   // Tools, weapons, armor
		CONTAINER,   // Chests, bags, boxes
		MISC         // Miscellaneous items
	}
}
