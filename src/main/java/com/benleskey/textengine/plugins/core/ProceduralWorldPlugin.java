package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
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
	
	// Landmark types for distant visibility
	private enum LandmarkType {
		GREAT_TREE, RUINED_TOWER
	}
	
	// Simple 2D coordinate for spatial tracking
	private static class Coord {
		final int x, y;
		
		Coord(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		double distanceTo(Coord other) {
			int dx = this.x - other.x;
			int dy = this.y - other.y;
			return Math.sqrt(dx * dx + dy * dy);
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Coord coord = (Coord) o;
			return x == coord.x && y == coord.y;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(x, y);
		}
	}
	
	// Generation parameters
	private final Random random;
	private final long seed;
	
	// Track generated places by biome for spatial coherence
	private Map<Biome, List<Entity>> placesByBiome = new HashMap<>();
	
	// Track spatial positions of places
	private Map<Entity, Coord> placePositions = new HashMap<>();
	private Map<Coord, Entity> positionToPlace = new HashMap<>();
	
	// Track all generated places for connection opportunities
	private List<Entity> allPlaces = new ArrayList<>();
	
	// Track landmarks for distant visibility
	private List<Entity> landmarks = new ArrayList<>();
	
	// Track starting location for new clients
	private Entity startingPlace;
	
	// Systems cached for lazy generation
	private EntitySystem entitySystem;
	private LookSystem lookSystem;
	private ConnectionSystem connectionSystem;
	private ItemSystem itemSystem;
	private RelationshipSystem relationshipSystem;
	private VisibilitySystem visibilitySystem;
	private EntityTagSystem entityTagSystem;
	
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
		relationshipSystem = rs;
		connectionSystem = game.getSystem(ConnectionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);
		visibilitySystem = game.getSystem(VisibilitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		
		// Register entity types
		entitySystem.registerEntityType(Place.class);
		entitySystem.registerEntityType(Actor.class);
		entitySystem.registerEntityType(Item.class);
		
		// Generate initial world (just starting place + planned exits)
		startingPlace = generateInitialWorld(entitySystem, lookSystem, rs, connectionSystem, ws);
		
		log.log("Procedural world initialized with starting place. Places will generate on exploration.");
	}
	
	@Override
	public void onStartClient(Client client) throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Create player actor
		Actor actor = es.add(Actor.class);
		ls.addLook(actor, "basic", "yourself");
		client.setEntity(actor);
		
		// Place actor in starting location
		rs.add(startingPlace, actor, rs.rvContains);
		
		// Send initial look command so player sees where they are
		CommandInput lookCommand = game.inputLineToCommandInput("look");
		game.feedCommand(client, lookCommand);
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
		
		// Generate only the starting place at origin (0, 0)
		Biome startingBiome = randomBiome();
		Entity starting = generatePlaceAtPosition(es, ls, startingBiome, new Coord(0, 0));
		placesByBiome.get(startingBiome).add(starting);
		allPlaces.add(starting);
		
		// Generate neighbors for the starting place so player has choices
		// Pass null for excludeDirection since there's no direction we came from
		generateNeighborsForPlace(starting, null);
		
		// Generate 2-3 distant landmarks at strategic positions
		generateLandmarks(es, ls, rs);
		
		// Update visibility for all existing places now that landmarks exist
		for (Entity place : allPlaces) {
			updateDistantVisibility(place);
		}
		
		return starting;
	}
	
	/**
	 * Generate a single place based on biome type at a specific position.
	 */
	private Entity generatePlaceAtPosition(EntitySystem es, LookSystem ls, Biome biome, Coord position) {
		Place place = es.add(Place.class);
		
		// Generate description based on biome
		String description = generatePlaceDescription(biome);
		ls.addLook(place, "basic", description);
		
		// Track spatial position
		placePositions.put(place, position);
		positionToPlace.put(position, place);
		
		// Generate items for this place based on biome
		generateItemsForPlace(place, biome);
		
		// Update distant visibility for landmarks
		updateDistantVisibility(place);
		
		log.log("Generated new place: %s (%s) at (%d, %d)", description, biome, position.x, position.y);
		
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
			String reverseLandmark = sourceDescription; // Plain description, highlighting happens in DisambiguationSystem
			
			// Check if reverse connection exists
			Optional<Entity> reverseExists = connectionSystem.findExit(destination, reverseLandmark, ws.getCurrentTime());
			if (reverseExists.isEmpty()) {
				connectionSystem.connect(destination, from, reverseLandmark);
			}
			
			return destination;
		}
		
		log.log("No existing exit '%s' from place %s, this shouldn't happen with spatial generation!", exitName, from.getId());
		
		// This shouldn't happen with our spatial system, but handle it gracefully
		// Find an empty adjacent position
		Coord fromPos = placePositions.get(from);
		if (fromPos == null) {
			log.log("ERROR: Source place has no position!");
			return from; // Fallback
		}
		
		// Try to find an empty adjacent spot
		List<Coord> adjacentPositions = Arrays.asList(
			new Coord(fromPos.x + 1, fromPos.y),
			new Coord(fromPos.x - 1, fromPos.y),
			new Coord(fromPos.x, fromPos.y + 1),
			new Coord(fromPos.x, fromPos.y - 1)
		);
		
		Coord newPos = null;
		for (Coord pos : adjacentPositions) {
			if (!positionToPlace.containsKey(pos)) {
				newPos = pos;
				break;
			}
		}
		
		if (newPos == null) {
			// All adjacent spots taken, spiral outward
			newPos = new Coord(fromPos.x + random.nextInt(3) - 1, fromPos.y + random.nextInt(3) - 1);
		}
		
		// Generate a new place at the found position
		Biome biome = randomBiome();
		Entity newPlace = generatePlaceAtPosition(entitySystem, lookSystem, biome, newPos);
		placesByBiome.get(biome).add(newPlace);
		allPlaces.add(newPlace);
		
		// Create connection from source to new place
		connectionSystem.connect(from, newPlace, exitName);
		
		// Create reverse connection
		List<com.benleskey.textengine.model.LookDescriptor> sourceLooks = 
			lookSystem.getLooksFromEntity(from, ws.getCurrentTime());
		String sourceDescription = sourceLooks.isEmpty() ? "back" : sourceLooks.get(0).getDescription();
		String reverseLandmark = sourceDescription; // Plain description, highlighting happens in DisambiguationSystem
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
	 * Uses spatial logic to sometimes connect to existing nearby places, creating loops.
	 * 
	 * @param place The place to generate neighbors for
	 * @param excludeDirection Direction to exclude (typically the direction we came from)
	 */
	private void generateNeighborsForPlace(Entity place, String excludeDirection) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Get current position
		Coord currentPos = placePositions.get(place);
		if (currentPos == null) {
			log.log("Warning: place %s has no position, cannot generate neighbors", place.getId());
			return;
		}
		
		// Get existing exits to avoid duplicates
		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = 
			connectionSystem.getConnections(place, ws.getCurrentTime());
		Set<String> usedLandmarks = existingExits.stream()
			.map(com.benleskey.textengine.model.ConnectionDescriptor::getExitName)
			.collect(java.util.stream.Collectors.toSet());
		
		// Get already connected places (don't reconnect to them)
		Set<Entity> alreadyConnected = existingExits.stream()
			.map(com.benleskey.textengine.model.ConnectionDescriptor::getTo)
			.collect(java.util.stream.Collectors.toSet());
		
		// Find potential adjacent positions (4 cardinal directions)
		List<Coord> adjacentPositions = Arrays.asList(
			new Coord(currentPos.x + 1, currentPos.y),  // east
			new Coord(currentPos.x - 1, currentPos.y),  // west
			new Coord(currentPos.x, currentPos.y + 1),  // north
			new Coord(currentPos.x, currentPos.y - 1)   // south
		);
		
		// Shuffle for variety
		Collections.shuffle(adjacentPositions, random);
		
		// Generate 2-4 neighbors
		int targetNeighborCount = 2 + random.nextInt(3); // 2-4 neighbors
		int generatedCount = 0;
		
		for (Coord adjacentPos : adjacentPositions) {
			if (generatedCount >= targetNeighborCount) break;
			
			// Check if there's already a place at this position
			Entity existingPlace = positionToPlace.get(adjacentPos);
			
			Entity neighbor;
			boolean isNewPlace;
			
			if (existingPlace != null && !alreadyConnected.contains(existingPlace)) {
				// Found an existing place - connect to it (creates a loop!)
				neighbor = existingPlace;
				isNewPlace = false;
				log.log("Connecting to existing place at (%d, %d) - creating loop!", adjacentPos.x, adjacentPos.y);
			} else if (existingPlace == null) {
				// Empty position - generate new place
				Biome neighborBiome = randomBiome();
				neighbor = generatePlaceAtPosition(entitySystem, lookSystem, neighborBiome, adjacentPos);
				placesByBiome.get(neighborBiome).add(neighbor);
				allPlaces.add(neighbor);
				isNewPlace = true;
			} else {
				// Position occupied by already-connected place, skip
				continue;
			}
			
			// Get the neighbor's description to use as the landmark name
			List<com.benleskey.textengine.model.LookDescriptor> neighborLooks = 
				lookSystem.getLooksFromEntity(neighbor, ws.getCurrentTime());
			String fullDescription = neighborLooks.isEmpty() ? "somewhere" : neighborLooks.get(0).getDescription();
			
			// Use plain description as landmark name (highlighting happens in DisambiguationSystem)
			// Numeric IDs handle disambiguation, so we don't need to make landmark names unique
			String landmarkName = fullDescription;
			
			// Skip if we already have a connection with this landmark name
			if (usedLandmarks.contains(landmarkName)) {
				continue;
			}
			
			usedLandmarks.add(landmarkName);
			
			// Create one-way connection FROM current place TO neighbor
			connectionSystem.connect(place, neighbor, landmarkName);
			generatedCount++;
			
			if (isNewPlace) {
				log.log("Created new neighbor at (%d, %d)", adjacentPos.x, adjacentPos.y);
			}
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
	/**
	 * Generate items for a place based on its biome type.
	 * Creates 2-5 items that fit the biome theme.
	 * 
	 * @param place The place to populate with items
	 * @param biome The biome type of the place
	 */
	private void generateItemsForPlace(Entity place, Biome biome) {
		try {
			// Generate 2-5 items for this place
			int itemCount = 2 + random.nextInt(4);
			
			for (int i = 0; i < itemCount; i++) {
				generateItemForBiome(place, biome);
			}
		} catch (Exception e) {
			log.log("Error generating items for place %d: %s", place.getId(), e.getMessage());
		}
	}
	
	/**
	 * Generate a single item appropriate for the biome and add it to the place.
	 */
	private void generateItemForBiome(Entity place, Biome biome) throws InternalException {
		String itemDescription;
		ItemSystem.ItemType itemType;
		long quantity = 1;
		long weight = 1;
		
		// Select item based on biome
		switch (biome) {
			case FOREST -> {
				itemDescription = randomChoice(
					"a stick",
					"some pine cones",
					"a fallen branch",
					"some moss",
					"a mushroom"
				);
				itemType = ItemSystem.ItemType.RESOURCE;
				// Sticks, moss, and mushrooms can be infinite or abundant
				if (itemDescription.contains("stick") || itemDescription.contains("moss")) {
					quantity = -1; // infinite
				} else {
					quantity = 5 + random.nextInt(10);
				}
			}
			case MEADOW -> {
				itemDescription = randomChoice(
					"some grass",
					"a wildflower",
					"a smooth pebble",
					"some seeds",
					"a blade of grass"
				);
				itemType = ItemSystem.ItemType.RESOURCE;
				// Grass is infinite
				if (itemDescription.contains("grass")) {
					quantity = -1; // infinite
				} else {
					quantity = 3 + random.nextInt(7);
				}
			}
			case RIVER -> {
				itemDescription = randomChoice(
					"a river stone",
					"some wet sand",
					"a smooth rock",
					"some reeds",
					"a piece of driftwood"
				);
				itemType = ItemSystem.ItemType.RESOURCE;
				// Stones and sand can be abundant
				if (itemDescription.contains("stone") || itemDescription.contains("sand") || itemDescription.contains("rock")) {
					quantity = -1; // infinite
				} else {
					quantity = 2 + random.nextInt(5);
				}
			}
			case HILLS -> {
				itemDescription = randomChoice(
					"a stone",
					"a chunk of rock",
					"some gravel",
					"a sharp stone",
					"a small boulder"
				);
				itemType = ItemSystem.ItemType.RESOURCE;
				// Stones are very abundant in hills
				quantity = -1; // infinite
			}
			case RUINS -> {
				itemDescription = randomChoice(
					"an old rusty sword",
					"a broken shield",
					"a tarnished coin",
					"a piece of pottery",
					"an ancient artifact",
					"some rubble",
					"a weathered scroll"
				);
				// Ruins can have equipment or misc items
				if (itemDescription.contains("sword") || itemDescription.contains("shield")) {
					itemType = ItemSystem.ItemType.EQUIPMENT;
					quantity = 1;
					weight = 5; // heavier
				} else if (itemDescription.contains("rubble")) {
					itemType = ItemSystem.ItemType.RESOURCE;
					quantity = -1; // infinite rubble
				} else {
					itemType = ItemSystem.ItemType.MISC;
					quantity = 1;
				}
			}
			default -> {
				itemDescription = "a small stone";
				itemType = ItemSystem.ItemType.RESOURCE;
				quantity = -1;
			}
		}
		
		// Create the item
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		Item item = es.add(Item.class);
		ls.addLook(item, "basic", itemDescription);
		
		// Set item properties
		itemSystem.setItemType(item, itemType);
		itemSystem.setQuantity(item, quantity);
		itemSystem.setWeight(item, weight);
		
		// Place the item in the location using the "contains" relationship
		relationshipSystem.add(place, item, relationshipSystem.rvContains);
		
		log.log("Generated item '%s' in place %d (qty: %d)", itemDescription, place.getId(), quantity);
	}
	
	/**
	 * Generate 2-3 distant landmarks (great trees, ruined towers) at strategic positions.
	 * These landmarks will be visible from multiple places.
	 */
	private void generateLandmarks(EntitySystem es, LookSystem ls, RelationshipSystem rs) {
		int landmarkCount = 2 + random.nextInt(2); // 2-3 landmarks
		
		for (int i = 0; i < landmarkCount; i++) {
			// Choose landmark type
			LandmarkType type = random.nextBoolean() ? LandmarkType.GREAT_TREE : LandmarkType.RUINED_TOWER;
			
			// Pick a random position offset from origin (between 3-6 units away)
			int distance = 3 + random.nextInt(4);
			double angle = random.nextDouble() * 2 * Math.PI;
			int x = (int) (Math.cos(angle) * distance);
			int y = (int) (Math.sin(angle) * distance);
			Coord landmarkPos = new Coord(x, y);
			
			// Create the landmark place
			Place landmark = es.add(Place.class);
			String description = generateLandmarkDescription(type);
			ls.addLook(landmark, "basic", description);
			
			// Mark as prominent so it's visible from distance
			entityTagSystem.addTag(landmark, visibilitySystem.tagProminent);
			
			// Track the landmark
			placePositions.put(landmark, landmarkPos);
			positionToPlace.put(landmarkPos, landmark);
			landmarks.add(landmark);
			allPlaces.add(landmark);
			
			log.log("Generated %s landmark '%s' at position (%d, %d)", 
				type, description, x, y);
		}
	}
	
	/**
	 * Generate evocative descriptions for landmarks based on type.
	 */
	private String generateLandmarkDescription(LandmarkType type) {
		return switch (type) {
			case GREAT_TREE -> randomChoice(
				"an ancient oak with massive spreading branches",
				"a colossal tree reaching toward the sky",
				"a gnarled elder tree twisted with age",
				"a towering sentinel tree draped in moss",
				"a great willow with cascading silver leaves"
			);
			case RUINED_TOWER -> randomChoice(
				"a crumbling stone tower against the horizon",
				"the broken spire of an ancient watchtower",
				"a weathered tower with ivy-covered walls",
				"the skeletal remains of a forgotten fortress",
				"a lone tower, half-collapsed and abandoned"
			);
		};
	}
	
	/**
	 * Update distant visibility when a new place is generated.
	 * Makes nearby landmarks visible from this place.
	 */
	private void updateDistantVisibility(Entity place) {
		Coord placePos = placePositions.get(place);
		if (placePos == null) return;
		
		// Check each landmark to see if it should be visible from this place
		for (Entity landmark : landmarks) {
			Coord landmarkPos = placePositions.get(landmark);
			if (landmarkPos == null) continue;
			
			// Make landmarks visible if they're 2-8 units away
			double distance = placePos.distanceTo(landmarkPos);
			if (distance >= 2.0 && distance <= 8.0) {
				visibilitySystem.makeVisibleFrom(place, landmark);
				log.log("Made landmark %d visible from place %d (distance: %.1f)", 
					landmark.getId(), place.getId(), distance);
			}
		}
	}
}

