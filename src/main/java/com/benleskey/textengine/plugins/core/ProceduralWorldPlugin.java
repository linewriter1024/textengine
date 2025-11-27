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
 * Generic procedural world generation plugin.
 * Generates places and connections dynamically based on registered content from other plugins.
 * No hardcoded biomes, items, or landmarks - all content comes from systems.
 * Follows the mission: "Everything is dynamic. Nothing is pre-built."
 * Uses SCALE_CONTINENT for all spatial operations.
 */
public class ProceduralWorldPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady, OnStartClient {
	
	// Generation parameters
	private final Random random;
	private final long seed;
	
	// Track generated places by biome for spatial coherence
	private Map<String, List<Entity>> placesByBiome = new HashMap<>();
	
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
	private SpatialSystem spatialSystem;
	private BiomeSystem biomeSystem;
	private PlaceDescriptionSystem placeDescriptionSystem;
	private ItemTemplateSystem itemTemplateSystem;
	private LandmarkTemplateSystem landmarkTemplateSystem;
	
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
		// Register all required systems
		game.registerSystem(new WorldSystem(game));
		game.registerSystem(new SpatialSystem(game));
		game.registerSystem(new BiomeSystem(game));
		game.registerSystem(new PlaceDescriptionSystem(game));
		game.registerSystem(new ItemTemplateSystem(game));
		game.registerSystem(new LandmarkTemplateSystem(game));
	}
	
	@Override
	public void onCoreSystemsReady() {
		log.log("Generating procedural world with seed %d...", seed);
		
		// Cache all systems for lazy generation
		entitySystem = game.getSystem(EntitySystem.class);
		lookSystem = game.getSystem(LookSystem.class);
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		connectionSystem = game.getSystem(ConnectionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);
		visibilitySystem = game.getSystem(VisibilitySystem.class);
		entityTagSystem = game.getSystem(EntityTagSystem.class);
		spatialSystem = game.getSystem(SpatialSystem.class);
		biomeSystem = game.getSystem(BiomeSystem.class);
		placeDescriptionSystem = game.getSystem(PlaceDescriptionSystem.class);
		itemTemplateSystem = game.getSystem(ItemTemplateSystem.class);
		landmarkTemplateSystem = game.getSystem(LandmarkTemplateSystem.class);
		
		// Set spatial system to 2D
		spatialSystem.setDimensions(2);
		
		// Register entity types
		entitySystem.registerEntityType(Place.class);
		entitySystem.registerEntityType(Actor.class);
		entitySystem.registerEntityType(Item.class);
		
		// Generate initial world (just starting place + planned exits)
		startingPlace = generateInitialWorld(entitySystem, lookSystem, relationshipSystem, connectionSystem, ws);
		
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
		for (String biomeName : biomeSystem.getAllBiomeNames()) {
			placesByBiome.put(biomeName, new ArrayList<>());
		}
		
		// Generate only the starting place at origin (0, 0)
		String startingBiome = biomeSystem.selectRandomBiome(random);
		Entity starting = generatePlaceAtPosition(es, ls, startingBiome, new int[]{0, 0});
		placesByBiome.get(startingBiome).add(starting);
		allPlaces.add(starting);
		
		// Generate neighbors for the starting place so player has choices
		generateNeighborsForPlace(starting);
		
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
	private Entity generatePlaceAtPosition(EntitySystem es, LookSystem ls, String biomeName, int[] position) {
		Place place = es.add(Place.class);
		
		// Generate description based on biome using PlaceDescriptionSystem
		String description = placeDescriptionSystem.generateDescription(biomeName, random);
		ls.addLook(place, "basic", description);
		
		// Track spatial position in SpatialSystem at continent scale
		spatialSystem.setPosition(place, SpatialSystem.SCALE_CONTINENT, position);
		
		// Generate items for this place based on biome
		generateItemsForPlace(place, biomeName);
		
		// Update distant visibility for landmarks
		updateDistantVisibility(place);
		
		log.log("Generated new place: %s (%s) at (%d, %d)", description, biomeName, position[0], position[1]);
		
		return place;
	}
	
	/**
	 * Ensure a place has neighboring places generated.
	 * Creates 2-4 random exits if the place has fewer than 2 connections.
	 */
	public synchronized void ensurePlaceHasNeighbors(Entity place) {
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
		generateNeighborsForPlace(place);
	}
	
	/**
	 * Generate 2-4 neighboring places for a location and create connections to them.
	 * Uses spatial logic to sometimes connect to existing nearby places, creating loops.
	 * 
	 * @param place The place to generate neighbors for
	 */
	private void generateNeighborsForPlace(Entity place) {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Get current position from SpatialSystem at continent scale
		int[] currentPos = spatialSystem.getPosition(place, SpatialSystem.SCALE_CONTINENT);
		if (currentPos == null) {
			log.log("Warning: place %s has no position, cannot generate neighbors", place.getId());
			return;
		}
		
		// Get existing exits to avoid duplicates
		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = 
			connectionSystem.getConnections(place, ws.getCurrentTime());
		
		// Get already connected places (don't reconnect to them)
		Set<Entity> alreadyConnected = existingExits.stream()
			.map(com.benleskey.textengine.model.ConnectionDescriptor::getTo)
			.collect(java.util.stream.Collectors.toSet());
		
		// Find potential adjacent positions (4 cardinal directions)
		List<int[]> adjacentPositions = spatialSystem.getAdjacentPositions(currentPos);
		
		// Shuffle for variety
		Collections.shuffle(adjacentPositions, random);
		
		// Generate 2-4 neighbors
		int targetNeighborCount = 2 + random.nextInt(3); // 2-4 neighbors
		int generatedCount = 0;
		
		for (int[] adjacentPos : adjacentPositions) {
			if (generatedCount >= targetNeighborCount) break;
			
			// Check if there's already a place at this position at continent scale
			Entity existingPlace = spatialSystem.getEntityAt(SpatialSystem.SCALE_CONTINENT, adjacentPos);
			
			Entity neighbor;
			boolean isNewPlace;
			
			if (existingPlace != null && !alreadyConnected.contains(existingPlace)) {
				// Found an existing place - connect to it (creates a loop!)
				neighbor = existingPlace;
				isNewPlace = false;
				log.log("Connecting to existing place at (%d, %d) - creating loop!", adjacentPos[0], adjacentPos[1]);
			} else if (existingPlace == null) {
				// Empty position - generate new place
				String neighborBiome = biomeSystem.selectRandomBiome(random);
				neighbor = generatePlaceAtPosition(entitySystem, lookSystem, neighborBiome, adjacentPos);
				placesByBiome.get(neighborBiome).add(neighbor);
				allPlaces.add(neighbor);
				isNewPlace = true;
			} else {
				// Position occupied by already-connected place, skip
				continue;
			}
			
			// Create one-way connection FROM current place TO neighbor
			connectionSystem.connect(place, neighbor);
			generatedCount++;
			
			if (isNewPlace) {
				log.log("Created new neighbor at (%d, %d)", adjacentPos[0], adjacentPos[1]);
			}
		}
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
	 * Creates 2-5 items that fit the biome theme using ItemTemplateSystem.
	 * 
	 * @param place The place to populate with items
	 * @param biomeName The biome type of the place
	 */
	private void generateItemsForPlace(Entity place, String biomeName) {
		try {
			// Generate 2-5 items for this place
			int itemCount = 2 + random.nextInt(4);
			
			for (int i = 0; i < itemCount; i++) {
				generateItemForBiome(place, biomeName);
			}
		} catch (Exception e) {
			log.log("Error generating items for place %d: %s", place.getId(), e.getMessage());
		}
	}
	
	/**
	 * Generate a single item appropriate for the biome and add it to the place using ItemTemplateSystem.
	 */
	private void generateItemForBiome(Entity place, String biomeName) throws InternalException {
		// Use ItemTemplateSystem to generate item data
		ItemTemplateSystem.ItemData itemData = itemTemplateSystem.generateItem(biomeName, game, random);
		
		if (itemData == null) {
			// No item generated for this biome
			return;
		}
		
		// Create the item entity
		Item item = entitySystem.add(Item.class);
		lookSystem.addLook(item, "basic", itemData.name());
		
		// Set default item properties (type, quantity, weight can be in itemData.properties if needed)
		ItemSystem.ItemType itemType = ItemSystem.ItemType.RESOURCE; // default
		long quantity = 1; // default
		long weight = 1; // default
		
		// Check if custom properties were set
		if (itemData.properties().containsKey("type")) {
			itemType = (ItemSystem.ItemType) itemData.properties().get("type");
		}
		if (itemData.properties().containsKey("quantity")) {
			quantity = (Long) itemData.properties().get("quantity");
		}
		if (itemData.properties().containsKey("weight")) {
			weight = (Long) itemData.properties().get("weight");
		}
		
		itemSystem.setItemType(item, itemType);
		itemSystem.setQuantity(item, quantity);
		itemSystem.setWeight(item, weight);
		
		// Note: Tags from itemData are for future use, not currently applied
		
		// Place the item in the location using the "contains" relationship
		relationshipSystem.add(place, item, relationshipSystem.rvContains);
		
		log.log("Generated item '%s' in place %d (qty: %d)", itemData.name(), place.getId(), quantity);
	}
	
	/**
	 * Generate 2-3 distant landmarks using LandmarkTemplateSystem.
	 * These landmarks will be visible from multiple places.
	 */
	private void generateLandmarks(EntitySystem es, LookSystem ls, RelationshipSystem rs) {
		int landmarkCount = 2 + random.nextInt(2); // 2-3 landmarks
		
		for (int i = 0; i < landmarkCount; i++) {
			// Choose landmark type from LandmarkTemplateSystem
			String landmarkTypeName = landmarkTemplateSystem.selectRandomLandmarkType(random);
			LandmarkTemplateSystem.LandmarkType landmarkType = 
				landmarkTemplateSystem.getLandmarkType(landmarkTypeName);
			
			// Pick a random position offset from origin (between 3-6 units away)
			int distance = 3 + random.nextInt(4);
			double angle = random.nextDouble() * 2 * Math.PI;
			int x = (int) (Math.cos(angle) * distance);
			int y = (int) (Math.sin(angle) * distance);
			int[] landmarkPos = new int[]{x, y};
			
			// Create the landmark place
			Place landmark = es.add(Place.class);
			String description = landmarkTemplateSystem.generateDescription(landmarkTypeName, random);
			ls.addLook(landmark, "basic", description);
			
			// Mark as prominent so it's visible from distance
			entityTagSystem.addTag(landmark, visibilitySystem.tagProminent);
			
			// Track spatial position at continent scale
			spatialSystem.setPosition(landmark, SpatialSystem.SCALE_CONTINENT, landmarkPos);
			landmarks.add(landmark);
			allPlaces.add(landmark);
			
			log.log("Generated %s landmark '%s' at position (%d, %d)", 
				landmarkTypeName, description, x, y);
		}
	}
	
	/**
	 * Update distant visibility when a new place is generated.
	 * Makes nearby landmarks visible from this place.
	 */
	private void updateDistantVisibility(Entity place) {
		int[] placePos = spatialSystem.getPosition(place, SpatialSystem.SCALE_CONTINENT);
		if (placePos == null) return;
		
		// Check each landmark to see if it should be visible from this place
		for (Entity landmark : landmarks) {
			int[] landmarkPos = spatialSystem.getPosition(landmark, SpatialSystem.SCALE_CONTINENT);
			if (landmarkPos == null) continue;
			
			// Calculate distance
			double distance = spatialSystem.distance(placePos, landmarkPos);
			
			// Make landmarks visible if they're within visibility range (2-8 units)
			if (distance >= 2.0 && distance <= 8.0) {
				visibilitySystem.makeVisibleFrom(place, landmark);
				log.log("Made landmark %d visible from place %d (distance: %.1f)", 
					landmark.getId(), place.getId(), distance);
			}
		}
	}
}

