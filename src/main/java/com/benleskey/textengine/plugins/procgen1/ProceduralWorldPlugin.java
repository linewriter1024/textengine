package com.benleskey.textengine.plugins.procgen1;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.entities.Actor;
import com.benleskey.textengine.entities.Avatar;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.Place;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnEntityTypesRegistered;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.hooks.core.OnStartClient;
import com.benleskey.textengine.model.ConnectionDescriptor;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.plugins.core.EntityPlugin;
import com.benleskey.textengine.plugins.core.EventPlugin;
import com.benleskey.textengine.plugins.procgen1.systems.ItemTemplateSystem;
import com.benleskey.textengine.plugins.procgen1.systems.LandmarkTemplateSystem;
import com.benleskey.textengine.plugins.procgen1.systems.PlaceDescriptionSystem;
import com.benleskey.textengine.systems.*;

import java.util.*;

/**
 * Generic procedural world generation plugin.
 * Generates places and connections dynamically based on registered content from
 * other plugins.
 * No hardcoded biomes, items, or landmarks - all content comes from systems.
 * Follows the mission: "Everything itemSystem dynamic. Nothing itemSystem
 * pre-built."
 * Uses SCALE_CONTINENT for all spatial operations.
 */
public class ProceduralWorldPlugin extends Plugin
		implements OnPluginInitialize, OnEntityTypesRegistered, OnStartClient {

	// Generation parameters
	private Random random;
	private long seed;

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
	private WorldSystem worldSystem;
	private ItemTemplateSystem itemTemplateSystem;
	private LandmarkTemplateSystem landmarkTemplateSystem;

	public ProceduralWorldPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EntityPlugin.class), game.getPlugin(EventPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		// Register all required systems
		game.registerSystem(new BiomeSystem(game));
		game.registerSystem(new PlaceDescriptionSystem(game));
		game.registerSystem(new ItemTemplateSystem(game));
		game.registerSystem(new LandmarkTemplateSystem(game));
		// ItemDescriptionSystem replaced by EntityDescriptionSystem (registered in
		// EntityPlugin)
	}

	@Override
	public void onEntityTypesRegistered() {
		// Get seed from WorldSystem (already initialized during system init)
		WorldSystem ws = game.getSystem(WorldSystem.class);
		seed = ws.getSeed();
		random = new Random(seed);

		log.log("Procedural world using seed %d", seed);

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
		worldSystem = ws; // Already fetched above

		// Set spatial system to 2D
		spatialSystem.setDimensions(2);

		// Register base entity types (custom types registered by content plugins in
		// OnCoreSystemsReady)
		entitySystem.registerEntityType(Avatar.class);
		entitySystem.registerEntityType(Place.class);
		entitySystem.registerEntityType(Actor.class);
		entitySystem.registerEntityType(Item.class);

		// Check if world already exists
		if (worldSystem.isWorldInitialized()) {
			// Load existing starting place from database
			startingPlace = loadStartingPlace();
			log.log("Loaded existing world with starting place at entity %d", startingPlace.getId());
		} else {
			// Generate initial world (starting place + neighbors + landmarks)
			startingPlace = generateInitialWorld();
			worldSystem.setWorldInitialized();
			log.log("Generated new procedural world with seed %d", seed);
		}
	}

	@Override
	public void onStartClient(Client client) throws InternalException {

		// Try to find existing actor or create new one
		Actor actor = findOrCreatePlayerActor();
		client.setEntity(actor);

		// Add a rattle to player's starting inventory
		com.benleskey.textengine.plugins.highfantasy.entities.Rattle rattle = com.benleskey.textengine.plugins.highfantasy.entities.Rattle
				.create(game, random);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		rs.add(actor, rattle, rs.rvContains);

		// Send initial look command so player sees where they are
		CommandInput lookCommand = game.inputLineToCommandInput("look");
		game.feedCommand(client, lookCommand);
	}

	/**
	 * Generate the initial world state: create starting place only.
	 * All other places generate on-demand as player explores.
	 */
	private Entity generateInitialWorld() {

		// Initialize biome tracking
		for (String biomeName : biomeSystem.getAllBiomeNames()) {
			placesByBiome.put(biomeName, new ArrayList<>());
		}

		// Load existing landmarks from database (if any)
		loadExistingLandmarks();

		// Generate only the starting place at origin (0, 0)
		String startingBiome = biomeSystem.selectRandomBiome(random);
		Entity starting = generatePlaceAtPosition(startingBiome, new int[] { 0, 0 });
		placesByBiome.get(startingBiome).add(starting);
		allPlaces.add(starting);

		// Generate neighbors for the starting place so player has choices
		generateNeighborsForPlace(starting);

		// Generate 2-3 distant landmarks at strategic positions (only if none exist
		// yet)
		if (landmarks.isEmpty()) {
			generateLandmarks();
		} else {
			log.log("Loaded %d existing landmarks from database", landmarks.size());
		}

		// Update visibility for all existing places now that landmarks exist
		for (Entity place : allPlaces) {
			updateDistantVisibility(place);
		}

		// Spawn a goblin NPC that patrols between starting and a neighbor
		if (!allPlaces.isEmpty()) {
			List<Entity> neighbors = connectionSystem.getConnections(starting, worldSystem.getCurrentTime()).stream()
					.map(ConnectionDescriptor::getTo)
					.toList();
			if (!neighbors.isEmpty()) {
				Entity neighbor = neighbors.get(0); // Pick first neighbor
				spawnGoblinNPC(starting, starting, neighbor);
			}
		}

		return starting;
	}

	/**
	 * Load the starting place (position 0,0) from the database.
	 */
	private Entity loadStartingPlace() throws InternalException {
		// Find place at position (0, 0) at continent scale
		int[] originPos = new int[] { 0, 0 };
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
	private Actor findOrCreatePlayerActor() throws InternalException {

		// Try to find existing avatar (player-controlled actor) in the starting
		// location
		List<com.benleskey.textengine.model.RelationshipDescriptor> actorsInStartingPlace = relationshipSystem
				.getReceivingRelationships(startingPlace, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.filter(rd -> rd.getReceiver() instanceof Actor)
				.filter(rd -> entityTagSystem.hasTag(rd.getReceiver(), entitySystem.TAG_AVATAR,
						worldSystem.getCurrentTime())) // Only avatars
				.toList();

		if (!actorsInStartingPlace.isEmpty()) {
			// Reuse existing actor
			Actor existingActor = (Actor) actorsInStartingPlace.get(0).getReceiver();
			log.log("Reconnecting to existing actor %s", existingActor);
			return existingActor;
		}

		// Create new actor
		Avatar actor = Avatar.create(game);
		itemSystem.addTag(actor, itemSystem.TAG_CARRY_WEIGHT, 10000);
		relationshipSystem.add(startingPlace, actor, relationshipSystem.rvContains);
		log.log("Created new actor %s", actor);

		// Give starting inventory (timepiece + grandfather clock)
		// Use deterministic random from world seed for consistent testing
		var timepiece = com.benleskey.textengine.plugins.highfantasy.entities.Timepiece.create(game, random);
		relationshipSystem.add(actor, timepiece, relationshipSystem.rvContains);

		// Add axe for testing tree cutting
		var axe = com.benleskey.textengine.plugins.highfantasy.entities.Axe.create(game, random);
		relationshipSystem.add(actor, axe, relationshipSystem.rvContains);

		var clock = com.benleskey.textengine.plugins.highfantasy.entities.clock.GrandfatherClock.create(game, random);
		relationshipSystem.add(startingPlace, clock, relationshipSystem.rvContains);

		// Add a wooden chest with items for testing container system
		var chest = com.benleskey.textengine.plugins.highfantasy.entities.WoodenChest.create(game, random);
		relationshipSystem.add(startingPlace, chest, relationshipSystem.rvContains);

		// Put some items in the chest for testing
		var coin = com.benleskey.textengine.plugins.highfantasy.entities.AncientCoin.create(game, random);
		relationshipSystem.add(chest, coin, relationshipSystem.rvContains);

		var scroll = com.benleskey.textengine.plugins.highfantasy.entities.WeatheredScroll.create(game, random);
		relationshipSystem.add(chest, scroll, relationshipSystem.rvContains);

		return actor;
	}

	/**
	 * Spawn a goblin NPC that patrols between two rooms.
	 */
	private void spawnGoblinNPC(Entity startingPlace, Entity roomA, Entity roomB) {
		// Spawn goblin in starting place (will patrol between roomA and roomB)
		var goblin = com.benleskey.textengine.plugins.highfantasy.entities.Goblin.create(
				game, startingPlace, roomA, roomB);

		log.log("Spawned goblin %d in place %d (will patrol between %d and %d)",
				goblin.getId(), startingPlace.getId(), roomA.getId(), roomB.getId());
	}

	/**
	 * Generate a single place based on biome type at a specific position.
	 */
	private Entity generatePlaceAtPosition(String biomeName, int[] position) {
		// Use coordinate-specific random for deterministic generation
		Random placeRandom = getRandomForCoordinate(position);

		Place place = entitySystem.add(Place.class);

		// Generate description based on biome using PlaceDescriptionSystem
		String description = placeDescriptionSystem.generateDescription(biomeName, placeRandom);
		lookSystem.addLook(place, lookSystem.LOOK_BASIC, description);

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

		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = connectionSystem.getConnections(place,
				worldSystem.getCurrentTime());

		// If place has 2 or more exits, it's already been explored
		if (existingExits.size() >= 2) {
			log.log("Place %s already has %d exits, skipping neighbor generation",
					place, existingExits.size());
			return;
		}

		log.log("Place %s only has %d exit(s), generating neighbors...",
				place, existingExits.size());

		// This place was created as a neighbor but never visited - generate its
		// neighbors now
		generateNeighborsForPlace(place);
	}

	/**
	 * Generate 2-4 neighboring places for a location and create connections to
	 * them.
	 * Uses spatial logic to sometimes connect to existing nearby places, creating
	 * loops.
	 * 
	 * @param place The place to generate neighbors for
	 */
	private void generateNeighborsForPlace(Entity place) {

		// Get current position from SpatialSystem at continent scale
		int[] currentPos = spatialSystem.getPosition(place, SpatialSystem.SCALE_CONTINENT);
		if (currentPos == null) {
			throw new InternalException(
					String.format("Place %s has no position, cannot generate neighbors", place.getId()));

		}

		// Get existing exits to avoid duplicates
		List<com.benleskey.textengine.model.ConnectionDescriptor> existingExits = connectionSystem.getConnections(place,
				worldSystem.getCurrentTime());

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
			if (generatedCount >= targetNeighborCount)
				break;

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
				neighbor = generatePlaceAtPosition(neighborBiome, adjacentPos);
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
	 * /**
	 * Generate items for a place based on its biome type.
	 * Creates 2-5 items that fit the biome theme using ItemTemplateSystem.
	 * 
	 * @param place       The place to populate with items
	 * @param biomeName   The biome type of the place
	 * @param placeRandom Random instance for this place's coordinate
	 */
	private void generateItemsForPlace(Entity place, String biomeName, Random placeRandom) {
		// Generate 2-5 items for this place
		int itemCount = 2 + placeRandom.nextInt(4);

		for (int i = 0; i < itemCount; i++) {
			generateItemForBiome(place, biomeName, placeRandom);
		}
	}

	/**
	 * Generate a single item appropriate for the biome and add it to the place
	 * using ItemTemplateSystem.
	 * If the item itemSystem a container (chest), populate it with 2-3 random
	 * items.
	 */
	private void generateItemForBiome(Entity place, String biomeName, Random placeRandom) throws InternalException {

		// Use ItemTemplateSystem to generate item factory
		ItemTemplateSystem.ItemFactory factory = itemTemplateSystem.generateItem(biomeName, game, placeRandom);

		if (factory == null) {
			// No item generated for this biome
			return;
		}

		// Create item using factory (passes Random for description variant selection)
		Item item = factory.create(game, placeRandom);

		// Place the item in the location using the "contains" relationship
		relationshipSystem.add(place, item, relationshipSystem.rvContains);

		// If item itemSystem a container (chest), populate it with 2-3 items
		if (itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, worldSystem.getCurrentTime())) {
			int numContainedItems = 2 + placeRandom.nextInt(2); // 2-3 items
			for (int i = 0; i < numContainedItems; i++) {
				generateItemInContainer(item, biomeName, placeRandom);
			}
		}
	}

	/**
	 * Generate an item inside a container.
	 */
	private void generateItemInContainer(Entity container, String biomeName, Random placeRandom)
			throws InternalException {

		// Generate item factory (avoid generating another container inside)
		ItemTemplateSystem.ItemFactory factory = itemTemplateSystem.generateItem(biomeName, game, placeRandom);

		if (factory == null) {
			// No item generated
			return;
		}

		// Create item using factory (passes Random for description variant selection)
		Item item = factory.create(game, placeRandom);

		// Skip containers inside containers
		if (itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, worldSystem.getCurrentTime())) {
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

		DTime now = worldSystem.getCurrentTime();

		try {
			// Query for all entities with the prominent tag
			var stmt = game.db().prepareStatement(
					"SELECT DISTINCT entity_id FROM entity_tag WHERE entity_tag_type = ? AND entity_tag_id IN " +
							game.getSystem(EventSystem.class).getValidEventsSubquery("entity_tag.entity_tag_id"));
			stmt.setLong(1, visibilitySystem.tagProminent.type());
			game.getSystem(EventSystem.class).setValidEventsSubqueryParameters(
					stmt, 2,
					game.getSystem(EntityTagSystem.class).etEntityTag,
					now);

			try (var relationshipSystem = stmt.executeQuery()) {

				while (relationshipSystem.next()) {
					Entity landmark = entitySystem.get(relationshipSystem.getLong(1));
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
	private void generateLandmarks() {
		int landmarkCount = 2 + random.nextInt(2); // 2-3 landmarks

		for (int i = 0; i < landmarkCount; i++) {
			// Choose landmark type from LandmarkTemplateSystem
			String landmarkTypeName = landmarkTemplateSystem.selectRandomLandmarkType(random);

			// Pick a random position offset from origin (between 3-6 units away)
			int distance = 3 + random.nextInt(4);
			double angle = random.nextDouble() * 2 * Math.PI;
			int x = (int) (Math.cos(angle) * distance);
			int y = (int) (Math.sin(angle) * distance);
			int[] landmarkPos = new int[] { x, y };

			// Create the landmark place
			Place landmark = entitySystem.add(Place.class);
			String description = landmarkTemplateSystem.generateDescription(landmarkTypeName, random);
			lookSystem.addLook(landmark, lookSystem.LOOK_BASIC, description);

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
	 * Update distant visibility when a new place itemSystem generated.
	 * Makes nearby landmarks visible from this place.
	 */
	private void updateDistantVisibility(Entity place) {
		int[] placePos = spatialSystem.getPosition(place, SpatialSystem.SCALE_CONTINENT);
		if (placePos == null)
			return;

		// Check each landmark to see if it should be visible from this place
		for (Entity landmark : landmarks) {
			int[] landmarkPos = spatialSystem.getPosition(landmark, SpatialSystem.SCALE_CONTINENT);
			if (landmarkPos == null)
				continue;

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
