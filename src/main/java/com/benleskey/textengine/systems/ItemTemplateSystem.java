package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;
import com.benleskey.textengine.model.UniqueType;

import java.util.*;

/**
 * ItemTemplateSystem manages templates for generating items.
 * Plugins can register item generators for different biomes.
 */
public class ItemTemplateSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Map from biome name to list of item generators
	private final Map<String, List<ItemGenerator>> generators = new HashMap<>();
	
	public ItemTemplateSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - all in-memory
			getSchema().setVersionNumber(1);
		}
	}
	
	/**
	 * Register an item generator for a biome.
	 * 
	 * @param biomeName The biome this generator applies to
	 * @param weight Selection weight (higher = more common)
	 * @param generator Function that takes (Game, Random) and returns generated item data
	 */
	public void registerItemGenerator(String biomeName, int weight, 
	                                   ItemGeneratorFunction generator) {
		if (weight < 1) {
			throw new IllegalArgumentException("Weight must be at least 1");
		}
		
		generators.computeIfAbsent(biomeName, k -> new ArrayList<>())
			.add(new ItemGenerator(weight, generator));
		
		log.log("Registered item generator for biome: " + biomeName);
	}
	
	/**
	 * Generate item data for a place in a given biome.
	 * 
	 * @param biomeName The biome
	 * @param game The game instance
	 * @param random Random number generator
	 * @return Generated ItemData, or null if no items should be generated
	 */
	public ItemData generateItem(String biomeName, Game game, Random random) {
		List<ItemGenerator> biomeGenerators = generators.get(biomeName);
		
		if (biomeGenerators == null || biomeGenerators.isEmpty()) {
			return null;
		}
		
		// Calculate total weight
		int totalWeight = biomeGenerators.stream()
			.mapToInt(ItemGenerator::weight)
			.sum();
		
		// Select generator by weight
		int randomValue = random.nextInt(totalWeight);
		int currentWeight = 0;
		
		for (ItemGenerator gen : biomeGenerators) {
			currentWeight += gen.weight();
			if (randomValue < currentWeight) {
				return gen.generator().generate(game, random);
			}
		}
		
		// Fallback (shouldn't happen)
		return biomeGenerators.get(0).generator().generate(game, random);
	}
	
	/**
	 * Check if generators are registered for a biome.
	 */
	public boolean hasGeneratorsFor(String biomeName) {
		List<ItemGenerator> biomeGenerators = generators.get(biomeName);
		return biomeGenerators != null && !biomeGenerators.isEmpty();
	}
	
	/**
	 * Item generator record.
	 */
	private record ItemGenerator(int weight, ItemGeneratorFunction generator) {}
	
	/**
	 * Functional interface for item generation.
	 */
	@FunctionalInterface
	public interface ItemGeneratorFunction {
		ItemData generate(Game game, Random random);
	}
	
	/**
	 * Item data record - contains all information needed to create an item.
	 * 
	 * @param name Item name/description
	 * @param tags UniqueType tags to apply to the item
	 * @param properties Optional additional properties
	 * @param entityClass Optional specific entity class to instantiate (defaults to Item.class)
	 */
	public record ItemData(String name, List<UniqueType> tags, Map<String, Object> properties, Class<? extends Item> entityClass) {
		
		public ItemData(String name) {
			this(name, List.of(), Map.of(), Item.class);
		}
		
		public ItemData(String name, List<UniqueType> tags) {
			this(name, tags, Map.of(), Item.class);
		}
		
		public ItemData(String name, List<UniqueType> tags, Map<String, Object> properties) {
			this(name, tags, properties, Item.class);
		}
		
		public ItemData(String name, List<UniqueType> tags, Class<? extends Item> entityClass) {
			this(name, tags, Map.of(), entityClass);
		}
		
		public ItemData(String name, Class<? extends Item> entityClass) {
			this(name, List.of(), Map.of(), entityClass);
		}
	}
}
