package com.benleskey.textengine.plugins.procgen1.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

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
	 * @param weight    Selection weight (higher = more common)
	 * @param factory   Factory function that creates items
	 */
	public void registerItemGenerator(String biomeName, int weight, ItemFactory factory) {
		if (weight < 1) {
			throw new IllegalArgumentException("Weight must be at least 1");
		}

		generators.computeIfAbsent(biomeName, k -> new ArrayList<>())
				.add(new ItemGenerator(weight, factory));

		log.log("Registered item generator for biome: " + biomeName);
	}

	/**
	 * Generate an item for a place in a given biome.
	 * 
	 * @param biomeName The biome
	 * @param game      The game instance
	 * @param random    Random number generator
	 * @return ItemFactory to create the item, or null if no items should be
	 *         generated
	 */
	public ItemFactory generateItem(String biomeName, Game game, Random random) {
		List<ItemGenerator> biomeGenerators = generators.get(biomeName);

		if (biomeGenerators == null || biomeGenerators.isEmpty()) {
			return null;
		}

		// Calculate total weight
		int totalWeight = biomeGenerators.stream()
				.mapToInt(ItemGenerator::weight)
				.sum();

		// Select factory by weight
		int randomValue = random.nextInt(totalWeight);
		int currentWeight = 0;

		for (ItemGenerator gen : biomeGenerators) {
			currentWeight += gen.weight();
			if (randomValue < currentWeight) {
				return gen.factory();
			}
		}

		// Fallback (shouldn't happen)
		return biomeGenerators.get(0).factory();
	}

	/**
	 * Check if generators are registered for a biome.
	 */
	public boolean hasGeneratorsFor(String biomeName) {
		List<ItemGenerator> biomeGenerators = generators.get(biomeName);
		return biomeGenerators != null && !biomeGenerators.isEmpty();
	}

	/**
	 * Item generator record - pairs weight with factory.
	 */
	private record ItemGenerator(int weight, ItemFactory factory) {
	}

	/**
	 * Functional interface for item factory - creates the actual item entity.
	 * Takes Game and Random to allow for variant selection.
	 */
	@FunctionalInterface
	public interface ItemFactory {
		Item create(Game game, Random random);
	}
}
