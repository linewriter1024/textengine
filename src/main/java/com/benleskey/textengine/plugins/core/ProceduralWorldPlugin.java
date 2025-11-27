package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Place;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnEntityTypesRegistered;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.hooks.core.OnStartClient;
import com.benleskey.textengine.model.DTime;
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
public class ProceduralWorldPlugin extends Plugin implements OnPluginInitialize, OnEntityTypesRegistered, OnStartClient {
	
	// Generation parameters
	private final Random random;
	private long seed;  // Will be set from WorldSystem or parameter
	private final Long providedSeed;  // Seed provided via constructor (may be null)
	
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
	
	public ProceduralWorldPlugin(Game game, Long seed) {
		super(game);
		this.providedSeed = seed;
		// Random will be initialized in onEntityTypesRegistered after seed is determined
		this.random = null;
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EntityPlugin.class), game.getPlugin(EventPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		// Register all required systems
		game.registerSystem(new WorldSystem(game));
		game.registerSystem(new TickSystem(game));
		game.registerSystem(new SpatialSystem(game));
		game.registerSystem(new BiomeSystem(game));
		game.registerSystem(new PlaceDescriptionSystem(game));
		game.registerSystem(new ItemTemplateSystem(game));
		game.registerSystem(new LandmarkTemplateSystem(game));
		game.registerSystem(new ItemDescriptionSystem(game));
	}
	
	@Override
	public void onEntityTypesRegistered() {
		// Determine seed: use persisted value if exists, otherwise use provided or generate
		WorldSystem ws = game.getSystem(WorldSystem.class);
		Long persistedSeed = ws.getSeed();
		
		if (persistedSeed != null) {
			// World already exists, use persisted seed
			seed = persistedSeed;
			log.log("Loading existing world with seed %d", seed);
		} else {
			// New world - use provided seed or generate from timestamp
			seed = (providedSeed != null) ? providedSeed : System.currentTimeMillis();
			ws.setSeed(seed);
			log.log("Generating new procedural world with seed %d", seed);
		}
		
		// Initialize random with determined seed
		// Note: We create a new Random here even for existing worlds to ensure
		// consistent state for any future procedural generation
		@SuppressWarnings("resource")
		Random newRandom = new Random(seed);
		// Update the field by using reflection workaround for final field
		// Actually, we already changed it to non-final above
		try {
			java.lang.reflect.Field randomField = ProceduralWorldPlugin.class.getDeclaredField("random");
			randomField.setAccessible(true);
			randomField.set(this, newRandom);
		} catch (Exception e) {
			throw new InternalException("Failed to initialize random generator", e);
		}
		
		log.log("Generating procedural world with seed %d...", seed);
		
		// Cache all systems for lazy generation
		entitySystem = game.getSystem(EntitySystem.class);
		lookSystem = game.getSystem(LookSystem.class);
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		connectionSystem = game.getSystem(ConnectionSystem.class);
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
		
		// Register base entity types (custom types registered by content plugins in OnCoreSystemsReady)
		entitySystem.registerEntityType(Place.class);
		entitySystem.registerEntityType(Actor.class);
		entitySystem.registerEntityType(Item.class);
		
		// Check if world already exists
		if (ws.isWorldInitialized()) {
			// Load existing starting place from database
			startingPlace = loadStartingPlace();
			log.log("Loaded existing world with starting place at entity %d", startingPlace.getId());
		} else {
			// Generate initial world (starting place + neighbors + landmarks)
			startingPlace = generateInitialWorld(entitySystem, lookSystem, relationshipSystem, connectionSystem, ws);
			ws.setWorldInitialized();
			log.log("Generated new procedural world with seed %d", seed);
		}
	}
	
	@Override
	public void onStartClient(Client client) throws InternalException {
		EntitySystem es = game.getSystem(EntitySystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		// Try to find existing actor or create new one
		Actor actor = findOrCreatePlayerActor(es, ls, is, rs);
		client.setEntity(actor);
		
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
		
		// Load existing landmarks from database (if any)
		loadExistingLandmarks();
		
		// Generate only the starting place at origin (0, 0)
		String startingBiome = biomeSystem.selectRandomBiome(random);
		Entity starting = generatePlaceAtPosition(es, ls, startingBiome, new int[]{0, 0});
		placesByBiome.get(startingBiome).add(starting);
		allPlaces.add(starting);
		
		// Generate neighbors for the starting place so player has choices
		generateNeighborsForPlace(starting);
		
		// Generate 2-3 distant landmarks at strategic positions (only if none exist yet)
		if (landmarks.isEmpty()) {
			generateLandmarks(es, ls, rs);
		} else {
			log.log("Loaded %d existing landmarks from database", landmarks.size());
		}
		
		// Update visibility for all existing places now that landmarks exist
		for (Entity place : allPlaces) {
			updateDistantVisibility(place);
		}
		
		return starting;
	}
	
	/**
	 * Load the starting place (position 0,0) from the database.
	 */
	private Entity loadStartingPlace() throws InternalException {
		// Find place at position (0, 0) at continent scale
		int[] originPos = new int[]{0, 0};
		Entity place = spatialSystem.getEntityAt(SpatialSystem.SCALE_CONTINENT, originPos);
		if (place == null) {
			throw new InternalException("Starting place not found at origin (0, 0)");
		}
		
		// Load existing landmarks
		loadExistingLandmarks();
		
		return place;
	}
	
	/**
	 * Find existing player actor or create a new one with starting inventory.
	 */
	private Actor findOrCreatePlayerActor(EntitySystem es, LookSystem ls, ItemSystem is, RelationshipSystem rs) throws InternalException {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Try to find existing actor in the starting location
		List<com.benleskey.textengine.model.RelationshipDescriptor> actorsInStartingPlace = 
			rs.getReceivingRelationships(startingPlace, rs.rvContains, ws.getCurrentTime())
			.stream()
			.filter(rd -> rd.getReceiver() instanceof Actor)
			.toList();
		
		if (!actorsInStartingPlace.isEmpty()) {
			// Reuse existing actor
			Actor existingActor = (Actor) actorsInStartingPlace.get(0).getReceiver();
			log.log("Reconnecting to existing actor %d", existingActor.getId());
			return existingActor;
		}
		
		// Create new actor
		Actor actor = es.add(Actor.class);
		ls.addLook(actor, "basic", "yourself");
		is.addTag(actor, is.TAG_CARRY_WEIGHT, 10000); // Can carry up to 10kg
		rs.add(startingPlace, actor, rs.rvContains);
		log.log("Created new actor %d", actor.getId());
		
		// Give starting inventory (timepiece + grandfather clock)
		// Use a new Random instance for starting items (non-deterministic, per-session)
		Random startingRandom = new Random();
		var timepiece = com.benleskey.textengine.plugins.highfantasy.entities.Timepiece.create(game, startingRandom);
		rs.add(actor, timepiece, rs.rvContains);
		log.log("Gave player starting timepiece");
		
		var clock = com.benleskey.textengine.plugins.highfantasy.entities.GrandfatherClock.create(game, startingRandom);
		rs.add(startingPlace, clock, rs.rvContains);
		log.log("Added grandfather clock to starting location");
		
		return actor;
	}
	
	/**
	 * Generate a single place based on biome type at a specific position.
	 */
	private Entity generatePlaceAtPosition(EntitySystem es, LookSystem ls, String biomeName, int[] position) {
		// Use coordinate-specific random for deterministic generation
		Random placeRandom = getRandomForCoordinate(position);
		
		Place place = es.add(Place.class);
		
		// Generate description based on biome using PlaceDescriptionSystem
		String description = placeDescriptionSystem.generateDescription(biomeName, placeRandom);
		ls.addLook(place, "basic", description);
		
		// Track spatial position in SpatialSystem at continent scale
		spatialSystem.setPosition(place, SpatialSystem.SCALE_CONTINENT, position);
		
		// Generate items for this place based on biome
		generateItemsForPlace(place, biomeName, placeRandom);
		
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
	 * Reserved for future use in procedural generation.
	 */
	@SuppressWarnings("unused")
	@SafeVarargs
	private final <T> T randomChoice(T... options) {
		return options[random.nextInt(options.length)];
	}
	
	/**
	 * Create a deterministic Random instance for a specific coordinate.
	 * This ensures the same coordinate always generates the same content.
	 * Combines world seed with coordinates using a simple hash.
	 */
	private Random getRandomForCoordinate(int[] position) {
		// Combine world seed with coordinates using cantor pairing
		// https://en.wikipedia.org/wiki/Pairing_function#Cantor_pairing_function
		long x = position[0];
		long y = position[1];
		long coordHash = ((x + y) * (x + y + 1) / 2) + y;
		long combinedSeed = seed ^ coordHash;
		return new Random(combinedSeed);
	}
	
	/**
	 * Extract a single-word keyword from a place description.
	/**
	 * Generate items for a place based on its biome type.
	 * Creates 2-5 items that fit the biome theme using ItemTemplateSystem.
	 * 
	 * @param place The place to populate with items
	 * @param biomeName The biome type of the place
	 * @param placeRandom Random instance for this place's coordinate
	 */
	private void generateItemsForPlace(Entity place, String biomeName, Random placeRandom) {
		try {
			// Generate 2-5 items for this place
			int itemCount = 2 + placeRandom.nextInt(4);
			
			for (int i = 0; i < itemCount; i++) {
				generateItemForBiome(place, biomeName, placeRandom);
			}
		} catch (Exception e) {
			log.log("Error generating items for place %d: %s", place.getId(), e.getMessage());
		}
	}
	
	/**
	 * Generate a single item appropriate for the biome and add it to the place using ItemTemplateSystem.
	 * If the item is a container (chest), populate it with 2-3 random items.
	 */
	private void generateItemForBiome(Entity place, String biomeName, Random placeRandom) throws InternalException {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Use ItemTemplateSystem to generate item data
		ItemTemplateSystem.ItemData itemData = itemTemplateSystem.generateItem(biomeName, game, placeRandom);
		
		if (itemData == null) {
			// No item generated for this biome
			return;
		}
		
		// Create item using factory (passes Random for description variant selection)
		Item item = itemData.factory().create(game, placeRandom);
		
		// Place the item in the location using the "contains" relationship
		relationshipSystem.add(place, item, relationshipSystem.rvContains);
		
		// If item is a container (chest), populate it with 2-3 items
		if (itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, ws.getCurrentTime())) {
			int numContainedItems = 2 + placeRandom.nextInt(2); // 2-3 items
			for (int i = 0; i < numContainedItems; i++) {
				generateItemInContainer(item, biomeName, placeRandom);
			}
		}
	}
	
	/**
	 * Generate an item inside a container.
	 */
	private void generateItemInContainer(Entity container, String biomeName, Random placeRandom) throws InternalException {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Generate item data (avoid generating another container inside)
		ItemTemplateSystem.ItemData itemData = itemTemplateSystem.generateItem(biomeName, game, placeRandom);
		
		if (itemData == null) {
			// No item generated
			return;
		}
		
		// Create item using factory (passes Random for description variant selection)
		Item item = itemData.factory().create(game, placeRandom);
		
		// Skip containers inside containers
		if (itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, ws.getCurrentTime())) {
			return;
		}
		
		// Place item inside the container using the "contains" relationship
		relationshipSystem.add(container, item, relationshipSystem.rvContains);
	}
	
	/**
	 * Load existing landmarks from the database.
	 * Landmarks are identified by having the "prominent" tag.
	 */
	private void loadExistingLandmarks() throws InternalException {
		WorldSystem ws = game.getSystem(WorldSystem.class);
		DTime now = ws.getCurrentTime();
		
		try {
			// Query for all entities with the prominent tag
			var stmt = game.db().prepareStatement(
				"SELECT DISTINCT entity_id FROM entity_tag WHERE entity_tag_type = ? AND entity_tag_id IN " +
				game.getSystem(EventSystem.class).getValidEventsSubquery("entity_tag.entity_tag_id")
			);
			stmt.setLong(1, visibilitySystem.tagProminent.type());
			game.getSystem(EventSystem.class).setValidEventsSubqueryParameters(
				stmt, 2, 
				game.getSystem(EntityTagSystem.class).etEntityTag, 
				now
			);
			
			try (var rs = stmt.executeQuery()) {
				EntitySystem es = game.getSystem(EntitySystem.class);
				while (rs.next()) {
					Entity landmark = es.get(rs.getLong(1));
					landmarks.add(landmark);
					allPlaces.add(landmark);
				}
			}
		} catch (Exception e) {
			throw new InternalException("Failed to load existing landmarks", e);
		}
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

