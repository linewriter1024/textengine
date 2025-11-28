package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.*;
import java.util.function.Function;

/**
 * LandmarkTemplateSystem manages landmark types and their descriptions.
 * Plugins can register landmark types that can appear in the world.
 */
public class LandmarkTemplateSystem extends SingletonGameSystem implements OnSystemInitialize {

	// Map from landmark type name to landmark definition
	private final Map<String, LandmarkType> landmarkTypes = new HashMap<>();

	// List of all landmark type names for random selection
	private final List<String> typeNames = new ArrayList<>();

	public LandmarkTemplateSystem(Game game) {
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
	 * Register a landmark type.
	 * 
	 * @param typeName             Unique type name (e.g., "great_tree",
	 *                             "ruined_tower")
	 * @param weight               Generation weight (higher = more common)
	 * @param visibilityRange      How far away this landmark can be seen (in
	 *                             spatial units)
	 * @param descriptionGenerator Function that generates descriptions
	 */
	public void registerLandmarkType(String typeName, int weight, double visibilityRange,
			Function<Random, String> descriptionGenerator) {
		if (landmarkTypes.containsKey(typeName)) {
			throw new IllegalArgumentException("Landmark type already registered: " + typeName);
		}
		if (weight < 1) {
			throw new IllegalArgumentException("Weight must be at least 1");
		}
		if (visibilityRange <= 0) {
			throw new IllegalArgumentException("Visibility range must be positive");
		}

		LandmarkType type = new LandmarkType(typeName, weight, visibilityRange, descriptionGenerator);
		landmarkTypes.put(typeName, type);
		typeNames.add(typeName);

		log.log("Registered landmark type: " + typeName + " (weight: " + weight +
				", visibility: " + visibilityRange + ")");
	}

	/**
	 * Get a landmark type by name.
	 */
	public LandmarkType getLandmarkType(String typeName) {
		return landmarkTypes.get(typeName);
	}

	/**
	 * Check if a landmark type is registered.
	 */
	public boolean hasLandmarkType(String typeName) {
		return landmarkTypes.containsKey(typeName);
	}

	/**
	 * Get all registered landmark type names.
	 */
	public Set<String> getAllLandmarkTypeNames() {
		return new HashSet<>(typeNames);
	}

	/**
	 * Select a random landmark type based on weights.
	 */
	public String selectRandomLandmarkType(Random random) {
		if (landmarkTypes.isEmpty()) {
			throw new IllegalStateException("No landmark types registered");
		}

		// Calculate total weight
		int totalWeight = landmarkTypes.values().stream()
				.mapToInt(LandmarkType::weight)
				.sum();

		// Random selection based on weights
		int randomValue = random.nextInt(totalWeight);
		int currentWeight = 0;

		for (LandmarkType type : landmarkTypes.values()) {
			currentWeight += type.weight();
			if (randomValue < currentWeight) {
				return type.typeName();
			}
		}

		// Fallback (shouldn't happen)
		return typeNames.get(0);
	}

	/**
	 * Generate a description for a landmark type.
	 */
	public String generateDescription(String typeName, Random random) {
		LandmarkType type = landmarkTypes.get(typeName);
		if (type == null) {
			throw new IllegalArgumentException("Unknown landmark type: " + typeName);
		}
		return type.descriptionGenerator().apply(random);
	}

	/**
	 * Landmark type definition.
	 * 
	 * @param typeName             Unique identifier
	 * @param weight               Generation weight
	 * @param visibilityRange      How far the landmark can be seen
	 * @param descriptionGenerator Function to generate descriptions
	 */
	public record LandmarkType(String typeName, int weight, double visibilityRange,
			Function<Random, String> descriptionGenerator) {
	}
}
