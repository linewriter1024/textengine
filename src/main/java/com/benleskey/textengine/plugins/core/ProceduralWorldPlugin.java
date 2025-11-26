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
	private static final int INITIAL_PLACE_COUNT = 7;
	private static final double CONNECTION_PROBABILITY = 0.4;
	private final Random random;
	private final long seed;
	
	// Track generated places by biome for spatial coherence
	private Map<Biome, List<Entity>> placesByBiome = new HashMap<>();
	
	// Track starting location for new clients
	private Entity startingPlace;
	
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
		
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Register entity types
		es.registerEntityType(Place.class);
		es.registerEntityType(Actor.class);
		
		// Generate initial world
		startingPlace = generateInitialWorld(es, ls, rs, cs, ws);
		
		log.log("Procedural world generated: %d places across %d biomes", 
			placesByBiome.values().stream().mapToInt(List::size).sum(),
			placesByBiome.size());
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
	 * Generate the initial world state based on procedural rules.
	 */
	private Entity generateInitialWorld(EntitySystem es, LookSystem ls, 
			RelationshipSystem rs, ConnectionSystem cs, WorldSystem ws) {
		
		// Initialize biome tracking
		for (Biome biome : Biome.values()) {
			placesByBiome.put(biome, new ArrayList<>());
		}
		
		// Generate initial set of places
		List<Entity> allPlaces = new ArrayList<>();
		for (int i = 0; i < INITIAL_PLACE_COUNT; i++) {
			Biome biome = randomBiome();
			Entity place = generatePlace(es, ls, biome);
			allPlaces.add(place);
			placesByBiome.get(biome).add(place);
		}
		
		// Generate connections between places based on spatial rules
		generateConnections(allPlaces, cs);
		
		// Return first place as starting location
		return allPlaces.get(0);
	}
	
	/**
	 * Generate a single place based on biome type.
	 */
	private Entity generatePlace(EntitySystem es, LookSystem ls, Biome biome) {
		Place place = es.add(Place.class);
		
		// Generate description based on biome
		String description = generatePlaceDescription(biome);
		ls.addLook(place, "basic", description);
		
		// TODO: Tag with biome for later reference (needs UniqueType)
		// EntityTagSystem ts = game.getSystem(EntityTagSystem.class);
		// ts.addTag(place, biome tag);
		
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
	 * Generate connections between places based on spatial rules.
	 */
	private void generateConnections(List<Entity> places, ConnectionSystem cs) {
		// Create a connected graph - ensure every place is reachable
		for (int i = 0; i < places.size() - 1; i++) {
			// Connect each place to the next to ensure connectivity
			Entity from = places.get(i);
			Entity to = places.get(i + 1);
			String direction = randomCardinalDirection();
			String reverse = oppositeDirection(direction);
			cs.connectBidirectional(from, to, direction, reverse);
		}
		
		// Add additional random connections for interesting topology
		for (int i = 0; i < places.size(); i++) {
			for (int j = i + 2; j < places.size(); j++) {
				if (random.nextDouble() < CONNECTION_PROBABILITY) {
					Entity from = places.get(i);
					Entity to = places.get(j);
					String direction = randomCardinalDirection();
					String reverse = oppositeDirection(direction);
					cs.connectBidirectional(from, to, direction, reverse);
				}
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
	 * Random cardinal direction.
	 */
	private String randomCardinalDirection() {
		String[] directions = {"north", "south", "east", "west"};
		return directions[random.nextInt(directions.length)];
	}
	
	/**
	 * Get opposite direction for bidirectional connections.
	 */
	private String oppositeDirection(String direction) {
		return switch (direction) {
			case "north" -> "south";
			case "south" -> "north";
			case "east" -> "west";
			case "west" -> "east";
			case "up" -> "down";
			case "down" -> "up";
			default -> direction; // For non-standard directions, use same
		};
	}
	
	/**
	 * Helper to randomly choose from options.
	 */
	@SafeVarargs
	private final <T> T randomChoice(T... options) {
		return options[random.nextInt(options.length)];
	}
}
