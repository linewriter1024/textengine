package com.benleskey.textengine.plugins.worldgen.procgen1.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.*;
import java.util.function.Function;

/**
 * PlaceDescriptionSystem manages templates for generating place descriptions.
 * Plugins can register description generators for different biomes.
 */
public class PlaceDescriptionSystem extends SingletonGameSystem implements OnSystemInitialize {

	// Map from biome name to list of description generators
	private final Map<String, List<DescriptionGenerator>> generators = new HashMap<>();

	public PlaceDescriptionSystem(Game game) {
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
	 * Register a description generator for a biome.
	 * 
	 * @param biomeName The biome this generator applies to
	 * @param weight    Selection weight (higher = more common)
	 * @param generator Function that takes Random and returns a description
	 */
	public void registerDescriptionGenerator(String biomeName, int weight,
			Function<Random, String> generator) {
		if (weight < 1) {
			throw new IllegalArgumentException("Weight must be at least 1");
		}

		generators.computeIfAbsent(biomeName, k -> new ArrayList<>())
				.add(new DescriptionGenerator(weight, generator));

		log.log("Registered description generator for biome: " + biomeName);
	}

	/**
	 * Generate a description for a place in a given biome.
	 * 
	 * @param biomeName The biome
	 * @param random    Random number generator
	 * @return Generated description
	 */
	public String generateDescription(String biomeName, Random random) {
		List<DescriptionGenerator> biomeGenerators = generators.get(biomeName);

		if (biomeGenerators == null || biomeGenerators.isEmpty()) {
			// Fallback if no generators registered
			return "an unremarkable area";
		}

		// Calculate total weight
		int totalWeight = biomeGenerators.stream()
				.mapToInt(DescriptionGenerator::weight)
				.sum();

		// Select generator by weight
		int randomValue = random.nextInt(totalWeight);
		int currentWeight = 0;

		for (DescriptionGenerator gen : biomeGenerators) {
			currentWeight += gen.weight();
			if (randomValue < currentWeight) {
				return gen.generator().apply(random);
			}
		}

		// Fallback (shouldn't happen)
		return biomeGenerators.get(0).generator().apply(random);
	}

	/**
	 * Check if generators are registered for a biome.
	 */
	public boolean hasGeneratorsFor(String biomeName) {
		List<DescriptionGenerator> biomeGenerators = generators.get(biomeName);
		return biomeGenerators != null && !biomeGenerators.isEmpty();
	}

	/**
	 * Description generator record.
	 */
	private record DescriptionGenerator(int weight, Function<Random, String> generator) {
	}
}
