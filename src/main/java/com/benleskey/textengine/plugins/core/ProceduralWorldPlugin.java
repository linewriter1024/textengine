package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Place;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.hooks.core.OnStartClient;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.*;

import java.util.*;

/**
 * Procedural world generation plugin.
 * Generates places and connections dynamically based on rules, not hardcoded content.
 * Follows the mission: "Everything is dynamic. Nothing is pre-built."
 */
public class ProceduralWorldPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady, OnStartClient {
	
	// Biome types for procedural generation
	private enum Biome {
		FOREST, MEADOW, RIVER, HILLS, RUINS
	}
	
	// Generation parameters
	private final Random random;
	private final long seed;
	
	// Track generated places by biome for spatial coherence
	private Map<Biome, List<Entity>> placesByBiome = new HashMap<>();
	
	// Track starting location for new clients
	private Entity startingPlace;
	
	// Systems cached for lazy generation
	private EntitySystem entitySystem;
	private LookSystem lookSystem;
	private ConnectionSystem connectionSystem;
	
	public ProceduralWorldPlugin(Game game) {
		this(game, System.currentTimeMillis());
	}
	
	public ProceduralWorldPlugin(Game game, long seed) {
		super(game);
		this.seed = seed;
		this.random = new Random(seed);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EntityPlugin.class), game.getPlugin(EventPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		// Register WorldSystem
		game.registerSystem(new WorldSystem(game));
	}
	
	@Override
	public void onCoreSystemsReady() {
		log.log("Generating procedural world with seed %d...", seed);
		
		// Cache systems for lazy generation
		entitySystem = game.getSystem(EntitySystem.class);
		lookSystem = game.getSystem(LookSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		connectionSystem = game.getSystem(ConnectionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Register entity types
		entitySystem.registerEntityType(Place.class);
		entitySystem.registerEntityType(Actor.class);
		
		// Generate initial world (just starting place + planned exits)
		startingPlace = generateInitialWorld(entitySystem, lookSystem, rs, connectionSystem, ws);
		
		log.log("Procedural world initialized with starting place. Places will generate on exploration.");
	}
	
	@Override
	public void onStartClient(Client client) throws InternalException {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Create player actor
		Actor actor = Actor.create(game);
		ls.addLook(actor, "basic", "yourself");
		client.setEntity(actor);
		
		// Place actor in starting location
		rs.add(startingPlace, actor, rs.rvContains);
	}
	
	/**
	 * Generate the initial world state: create starting place only.
	 * All other places generate on-demand as player explores.
	 */
	private Entity generateInitialWorld(EntitySystem es, LookSystem ls, 
			RelationshipSystem rs, ConnectionSystem cs, WorldSystem ws) {
		
		// Initialize biome tracking
		for (Biome biome : Biome.values()) {
			placesByBiome.put(biome, new ArrayList<>());
		}
		
		// Generate only the starting place
		Biome startingBiome = randomBiome();
		Entity starting = generatePlace(es, ls, startingBiome);
		placesByBiome.get(startingBiome).add(starting);
		
		// Generate neighbors for the starting place so player has choices
		// Pass null for excludeDirection since there's no direction we came from
		generateNeighborsForPlace(starting, null);
		
		return starting;
	}
	
	/**
	 * Generate a single place based on biome type.
	 */
	private Entity generatePlace(EntitySystem es, LookSystem ls, Biome biome) {
		Place place = es.add(Place.class);
		
		// Generate description based on biome
		String description = generatePlaceDescription(biome);
		ls.addLook(place, "basic", description);
		
		log.log("Generated new place: %s (%s)", description, biome);
		
		return place;
	}
	
	/**
	 * Generate a description for a place based on its biome.
	 * In a full implementation, this would use LLM with biome as context.
	 */
	private String generatePlaceDescription(Biome biome) {
		// These are procedural templates - later replace with LLM generation
		return switch (biome) {
			case FOREST -> randomChoice(
				"dense forest with towering trees",
				"sunlit clearing in the woods",
				"dark grove with twisted branches",
				"forest path covered in fallen leaves"
			);
			case MEADOW -> randomChoice(
				"peaceful meadow with wildflowers",
				"grassy field swaying in the breeze",
				"open meadow dotted with small stones",
				"rolling grassland under open sky"
			);
			case RIVER -> randomChoice(
				"riverbank with flowing water",
				"shallow ford across the stream",
				"bend in the river with gentle current",
				"rocky shore beside the water"
			);
			case HILLS -> randomChoice(
				"rocky hillside with sparse vegetation",
				"gentle slope overlooking the valley",
				"hilltop with a view of the surroundings",
				"boulder-strewn incline"
			);
			case RUINS -> randomChoice(
				"crumbling stone structure",
				"ancient ruins overgrown with vines",
				"weathered remnants of a forgotten place",
				"broken pillars and scattered rubble"
			);
		};
	}
	
	/**
	 * Generate a place for an exit if it doesn't exist yet.
	 * Called by NavigationPlugin when player tries to use an exit.
	 * 
	 * @param from The place the player is exiting from
	 * @param exitName The name of the exit being used (e.g., "north", "south")
	 * @return The destination place (newly generated or existing)
	 */
	public synchronized Entity generatePlaceForExit(Entity from, String exitName) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Check if this exit already has a connection to a real place
		Optional<Entity> existing = connectionSystem.findExit(from, exitName, ws.getCurrentTime());
		if (existing.isPresent()) {
			// Place exists, but check if it needs neighbors generated
			Entity destination = existing.get();
			log.log("Navigating to existing place %s, checking for neighbors...", destination.getId());
			ensurePlaceHasNeighbors(destination);
			
			// Add reverse connection if it doesn't exist
			// The reverse landmark is the source place's description
			List<com.benleskey.textengine.model.LookDescriptor> sourceLooks = 
				lookSystem.getLooksFromEntity(from, ws.getCurrentTime());
			String sourceDescription = sourceLooks.isEmpty() ? "back" : sourceLooks.get(0).getDescription();
			String keyword = extractKeyword(sourceDescription);
			String reverseLandmark = highlightKeywordInDescription(sourceDescription, keyword);
			
			// Check if reverse connection exists
			Optional<Entity> reverseExists = connectionSystem.findExit(destination, reverseLandmark, ws.getCurrentTime());
			if (reverseExists.isEmpty()) {
				connectionSystem.connect(destination, from, reverseLandmark);
			}
			
			return destination;
		}
		
		log.log("No existing exit '%s' from place %s, generating new place...", exitName, from.getId());
		
		// Generate a new place - the exitName should describe what kind of place it is
		// For now, use a random biome, but later we can parse the exitName to determine biome
		Biome biome = randomBiome();
		Entity newPlace = generatePlace(entitySystem, lookSystem, biome);
		placesByBiome.get(biome).add(newPlace);
		
		// Create connection from source to new place
		connectionSystem.connect(from, newPlace, exitName);
		
		// Create reverse connection
		List<com.benleskey.textengine.model.LookDescriptor> sourceLooks = 
			lookSystem.getLooksFromEntity(from, ws.getCurrentTime());
		String sourceDescription = sourceLooks.isEmpty() ? "back" : sourceLooks.get(0).getDescription();
		String keyword = extractKeyword(sourceDescription);
		String reverseLandmark = highlightKeywordInDescription(sourceDescription, keyword);
		connectionSystem.connect(newPlace, from, reverseLandmark);
		
		// Generate neighboring places and exits from the new place
		generateNeighborsForPlace(newPlace, reverseLandmark);
		
		return newPlace;
	}
	
	/**
	 * Ensure a place has neighboring places generated.
	 * If the place only has one exit (the one we came from), generate more neighbors.
	 */
	private void ensurePlaceHasNeighbors(Entity place) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = 
			connectionSystem.getConnections(place, ws.getCurrentTime());
		
		// If place has 2 or more exits, it's already been explored
		if (existingExits.size() >= 2) {
			log.log("Place %s already has %d exits, skipping neighbor generation", 
				place.getId(), existingExits.size());
			return;
		}
		
		log.log("Place %s only has %d exit(s), generating neighbors...", 
			place.getId(), existingExits.size());
		
		// This place was created as a neighbor but never visited - generate its neighbors now
		String excludeDirection = existingExits.isEmpty() ? null : existingExits.get(0).getExitName();
		generateNeighborsForPlace(place, excludeDirection);
	}
	
	/**
	 * Generate 2-4 neighboring places for a location and create connections to them.
	 * The neighboring places are created but their own exits are NOT generated yet.
	 * 
	 * @param place The place to generate neighbors for
	 * @param excludeDirection Direction to exclude (typically the direction we came from)
	 */
	private void generateNeighborsForPlace(Entity place, String excludeDirection) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Get existing exits to avoid duplicates
		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = 
			connectionSystem.getConnections(place, ws.getCurrentTime());
		Set<String> usedLandmarks = existingExits.stream()
			.map(com.benleskey.textengine.model.ConnectionDescriptor::getExitName)
			.collect(java.util.stream.Collectors.toSet());
		
		// Generate 2-4 neighboring places
		int neighborCount = 2 + random.nextInt(3); // 2-4 neighbors
		
		for (int i = 0; i < neighborCount; i++) {
			// Generate a neighboring place
			Biome neighborBiome = randomBiome();
			Entity neighbor = generatePlace(entitySystem, lookSystem, neighborBiome);
			placesByBiome.get(neighborBiome).add(neighbor);
			
			// Get the neighbor's description to use as the landmark name
			List<com.benleskey.textengine.model.LookDescriptor> neighborLooks = 
				lookSystem.getLooksFromEntity(neighbor, ws.getCurrentTime());
			String fullDescription = neighborLooks.isEmpty() ? "somewhere" : neighborLooks.get(0).getDescription();
			
			// Extract keyword and create highlighted landmark name
			String keyword = extractKeyword(fullDescription);
			String landmarkName = highlightKeywordInDescription(fullDescription, keyword);
			
			// Ensure uniqueness - if we already have a connection to this landmark, skip
			if (usedLandmarks.contains(landmarkName)) {
				// Try adding a number to make it unique
				int suffix = 2;
				String uniqueName = landmarkName;
				while (usedLandmarks.contains(uniqueName) && suffix < 10) {
					uniqueName = highlightKeywordInDescription(fullDescription, keyword + suffix);
					suffix++;
				}
				if (!usedLandmarks.contains(uniqueName)) {
					landmarkName = uniqueName;
				} else {
					continue; // Skip this one if we can't make it unique
				}
			}
			usedLandmarks.add(landmarkName);
			
			// Create one-way connection FROM current place TO neighbor
			// (The reverse connection is added when player visits the neighbor)
			connectionSystem.connect(place, neighbor, landmarkName);
		}
	}
	
	/**
	 * Select a random biome with weighted probabilities.
	 */
	private Biome randomBiome() {
		// Weight biomes - more forests and meadows, fewer ruins
		double r = random.nextDouble();
		if (r < 0.35) return Biome.FOREST;
		if (r < 0.65) return Biome.MEADOW;
		if (r < 0.80) return Biome.RIVER;
		if (r < 0.92) return Biome.HILLS;
		return Biome.RUINS;
	}
	
	/**
	 * Helper to randomly choose from options.
	 */
	@SafeVarargs
	private final <T> T randomChoice(T... options) {
		return options[random.nextInt(options.length)];
	}
	
	/**
	 * Extract a single-word keyword from a place description.
	 * Uses heuristics to find the most meaningful noun in the description.
	 */
	private String extractKeyword(String description) {
		if (description == null || description.isEmpty()) {
			return "place";
		}
		
		String[] words = description.split("\\s+");
		
		// Skip common articles and adjectives, look for the key noun
		Set<String> skipWords = Set.of("a", "an", "the", "with", "in", "of", "under", "over", "beside", "near");
		
		for (String word : words) {
			String cleaned = word.toLowerCase().replaceAll("[^a-z]", "");
			if (!cleaned.isEmpty() && !skipWords.contains(cleaned)) {
				return cleaned;
			}
		}
		
		// Fallback: use the first word
		return words.length > 0 ? words[0].toLowerCase().replaceAll("[^a-z]", "") : "place";
	}
	
	/**
	 * Highlight a keyword within a description using markup.
	 * Creates a string like "dense <em>forest</em> with towering trees"
	 */
	private String highlightKeywordInDescription(String description, String keyword) {
		if (description == null || description.isEmpty() || keyword == null || keyword.isEmpty()) {
			return description;
		}
		
		// Case-insensitive search for the keyword within the description
		String lowerDescription = description.toLowerCase();
		String lowerKeyword = keyword.toLowerCase();
		
		int index = lowerDescription.indexOf(lowerKeyword);
		if (index == -1) {
			// Keyword not found, just return description
			return description;
		}
		
		// Build highlighted version
		return description.substring(0, index) + 
		       "<em>" + description.substring(index, index + keyword.length()) + "</em>" +
		       description.substring(index + keyword.length());
	}
}
