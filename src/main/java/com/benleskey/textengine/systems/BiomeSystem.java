package com.benleskey.textengine.systems;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.SingletonGameSystem;
import com.benleskey.textengine.hooks.core.OnSystemInitialize;

import java.util.*;

/**
 * BiomeSystem manages biome types that can be registered by plugins.
 * Biomes define the environmental characteristics of generated places.
 * Generic and extensible - any plugin can register new biome types.
 */
public class BiomeSystem extends SingletonGameSystem implements OnSystemInitialize {
	
	// Map from biome name to biome data
	private final Map<String, Biome> biomes = new HashMap<>();
	
	// List of all biome names for weighted random selection
	private final List<String> biomeNames = new ArrayList<>();
	
	public BiomeSystem(Game game) {
		super(game);
	}
	
	@Override
	public void onSystemInitialize() {
		int v = getSchema().getVersionNumber();
		if (v == 0) {
			// No database tables needed - all in-memory for performance
			getSchema().setVersionNumber(1);
		}
	}
	
	/**
	 * Register a new biome type.
	 * 
	 * @param name Unique biome name (e.g., "forest", "desert", "ocean")
	 * @param weight Generation weight (higher = more common)
	 * @param properties Optional properties map for plugin-specific data
	 * @return The registered Biome
	 */
	public Biome registerBiome(String name, int weight, Map<String, Object> properties) {
		if (biomes.containsKey(name)) {
			throw new IllegalArgumentException("Biome already registered: " + name);
		}
		if (weight < 1) {
			throw new IllegalArgumentException("Biome weight must be at least 1");
		}
		
		Biome biome = new Biome(name, weight, properties != null ? properties : new HashMap<>());
		biomes.put(name, biome);
		biomeNames.add(name);
		
		log.log("Registered biome: " + name + " (weight: " + weight + ")");
		return biome;
	}
	
	/**
	 * Register a biome with no additional properties.
	 */
	public Biome registerBiome(String name, int weight) {
		return registerBiome(name, weight, null);
	}
	
	/**
	 * Get a biome by name.
	 * 
	 * @param name Biome name
	 * @return Biome, or null if not found
	 */
	public Biome getBiome(String name) {
		return biomes.get(name);
	}
	
	/**
	 * Check if a biome is registered.
	 */
	public boolean hasBiome(String name) {
		return biomes.containsKey(name);
	}
	
	/**
	 * Get all registered biome names.
	 */
	public Set<String> getAllBiomeNames() {
		return new HashSet<>(biomeNames);
	}
	
	/**
	 * Select a random biome based on weights.
	 * 
	 * @param random Random number generator
	 * @return Random biome name
	 */
	public String selectRandomBiome(Random random) {
		if (biomes.isEmpty()) {
			throw new IllegalStateException("No biomes registered");
		}
		
		// Calculate total weight
		int totalWeight = biomes.values().stream()
			.mapToInt(Biome::weight)
			.sum();
		
		// Random selection based on weights
		int randomValue = random.nextInt(totalWeight);
		int currentWeight = 0;
		
		for (Biome biome : biomes.values()) {
			currentWeight += biome.weight();
			if (randomValue < currentWeight) {
				return biome.name();
			}
		}
		
		// Fallback (shouldn't happen)
		return biomeNames.get(0);
	}
	
	/**
	 * Biome data record.
	 * 
	 * @param name Unique biome identifier
	 * @param weight Generation weight (higher = more common)
	 * @param properties Plugin-specific properties
	 */
	public record Biome(String name, int weight, Map<String, Object> properties) {
		
		/**
		 * Get a property value.
		 * 
		 * @param key Property key
		 * @return Property value, or null if not set
		 */
		public Object getProperty(String key) {
			return properties.get(key);
		}
		
		/**
		 * Get a property value with type safety.
		 */
		@SuppressWarnings("unchecked")
		public <T> Optional<T> getPropertyTyped(String key, Class<T> type) {
			Object value = properties.get(key);
			if (value == null) {
				return Optional.empty();
			}
			if (!type.isInstance(value)) {
				throw new ClassCastException(
					"Property '" + key + "' is not of type " + type.getName());
			}
			return Optional.of((T) value);
		}
		
		/**
		 * Check if a property exists.
		 */
		public boolean hasProperty(String key) {
			return properties.containsKey(key);
		}
	}
}
