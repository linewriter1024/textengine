package com.benleskey.textengine.plugins.highfantasy;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.plugins.highfantasy.entities.Axe;
import com.benleskey.textengine.plugins.highfantasy.entities.Rattle;
import com.benleskey.textengine.plugins.highfantasy.entities.Tree;
import com.benleskey.textengine.systems.*;

import java.util.*;

/**
 * HighFantasyPlugin registers fantasy-themed content for procedural world generation.
 * This includes biomes (forests, meadows, ruins), items (swords, coins), and landmarks.
 * 
 * Separated from ProceduralWorldPlugin to demonstrate genre-specific content registration.
 */
public class HighFantasyPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady {
	
	public HighFantasyPlugin(Game game) {
		super(game);
	}
	
	@Override
	public Set<Plugin> getDependencies() {
		// Ensure ProceduralWorldPlugin is loaded first so systems are registered
		return Set.of(
			game.getPlugin(com.benleskey.textengine.plugins.core.EntityPlugin.class),
			game.getPlugin(com.benleskey.textengine.plugins.core.ProceduralWorldPlugin.class)
		);
	}
	
	@Override
	public void onPluginInitialize() {
		log.log("Registering high fantasy content...");
		
		registerBiomes();
		registerPlaceDescriptions();
		registerItems();
		registerLandmarks();
		
		log.log("High fantasy content registered");
	}
	
	@Override
	public void onCoreSystemsReady() {
		log.log("Registering high fantasy entity types...");
		// Register entity types (used by items generated after this point)
		registerEntityTypes();
		// Register item descriptions (needs ItemSystem tags initialized)
		registerItemDescriptions();
		log.log("High fantasy setup complete");
	}
	
	private void registerEntityTypes() {
		EntitySystem es = game.getSystem(EntitySystem.class);
		
		// Register custom entity types for high fantasy items
		es.registerEntityType(Rattle.class);
		es.registerEntityType(Axe.class);
		es.registerEntityType(Tree.class);
		
		log.log("Registered 3 high fantasy entity types");
	}
	
	private void registerBiomes() {
		BiomeSystem bs = game.getSystem(BiomeSystem.class);
		
		// Register fantasy biomes with weights
		bs.registerBiome("forest", 30);
		bs.registerBiome("meadow", 25);
		bs.registerBiome("river", 15);
		bs.registerBiome("hills", 20);
		bs.registerBiome("ruins", 10);
		
		log.log("Registered 5 fantasy biomes");
	}
	
	private void registerPlaceDescriptions() {
		PlaceDescriptionSystem pds = game.getSystem(PlaceDescriptionSystem.class);
		
		// Forest descriptions
		pds.registerDescriptionGenerator("forest", 3, r -> "a dense forest");
		pds.registerDescriptionGenerator("forest", 2, r -> "a dark woodland");
		pds.registerDescriptionGenerator("forest", 2, r -> "a grove of ancient trees");
		pds.registerDescriptionGenerator("forest", 1, r -> "a misty forest path");
		
		// Meadow descriptions
		pds.registerDescriptionGenerator("meadow", 3, r -> "a grassy meadow");
		pds.registerDescriptionGenerator("meadow", 2, r -> "a sunlit clearing");
		pds.registerDescriptionGenerator("meadow", 2, r -> "a field of wildflowers");
		pds.registerDescriptionGenerator("meadow", 1, r -> "a peaceful glade");
		
		// River descriptions
		pds.registerDescriptionGenerator("river", 3, r -> "a flowing river");
		pds.registerDescriptionGenerator("river", 2, r -> "a babbling brook");
		pds.registerDescriptionGenerator("river", 2, r -> "a rushing stream");
		pds.registerDescriptionGenerator("river", 1, r -> "a calm waterway");
		
		// Hills descriptions
		pds.registerDescriptionGenerator("hills", 3, r -> "rolling hills");
		pds.registerDescriptionGenerator("hills", 2, r -> "a rocky hillside");
		pds.registerDescriptionGenerator("hills", 2, r -> "a windswept rise");
		pds.registerDescriptionGenerator("hills", 1, r -> "a grassy knoll");
		
		// Ruins descriptions
		pds.registerDescriptionGenerator("ruins", 3, r -> "ancient ruins");
		pds.registerDescriptionGenerator("ruins", 2, r -> "crumbling stonework");
		pds.registerDescriptionGenerator("ruins", 2, r -> "weathered ruins");
		pds.registerDescriptionGenerator("ruins", 1, r -> "a forgotten monument");
		
		log.log("Registered place descriptions for all biomes");
	}
	
	private void registerItems() {
		ItemTemplateSystem its = game.getSystem(ItemTemplateSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		// Forest items
		its.registerItemGenerator("forest", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData("a fallen branch"));
		its.registerItemGenerator("forest", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData("some wild mushrooms"));
		its.registerItemGenerator("forest", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a bird's feather"));
		// Trees (can be cut down with axe)
		its.registerItemGenerator("forest", 4, (g, r) -> 
			new ItemTemplateSystem.ItemData("a tree", List.of(is.TAG_CUTTABLE), Tree.class));
		// Axes (tools for cutting trees)
		its.registerItemGenerator("forest", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData("a rusty axe", List.of(is.TAG_TOOL, is.TAG_CUT), Axe.class));
		
		// Meadow items
		its.registerItemGenerator("meadow", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData("some grass"));
		its.registerItemGenerator("meadow", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData("a wildflower"));
		its.registerItemGenerator("meadow", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a smooth pebble"));
		// Toy rattles (make sound when used)
		its.registerItemGenerator("meadow", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData("a wooden toy rattle", List.of(is.TAG_TOY), Rattle.class));
		
		// River items
		its.registerItemGenerator("river", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData("a river stone"));
		its.registerItemGenerator("river", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData("a piece of driftwood"));
		its.registerItemGenerator("river", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a wet leaf"));
		
		// Hills items
		its.registerItemGenerator("hills", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData("a chunk of granite"));
		its.registerItemGenerator("hills", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData("some scraggly moss"));
		its.registerItemGenerator("hills", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a twisted root"));
		
		// Ruins items
		its.registerItemGenerator("ruins", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData("a piece of rubble"));
		its.registerItemGenerator("ruins", 4, (g, r) -> 
			new ItemTemplateSystem.ItemData("an ancient coin"));
		its.registerItemGenerator("ruins", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData("a rusty sword"));
		its.registerItemGenerator("ruins", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a tarnished helmet"));
		its.registerItemGenerator("ruins", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData("a weathered scroll"));
		// Wooden chests (containers - can hold items)
		its.registerItemGenerator("ruins", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData("a wooden chest", List.of(is.TAG_CONTAINER)));
		
		// Add chests and axes to all biomes (low probability)
		for (String biome : List.of("forest", "meadow", "river", "hills", "ruins")) {
			if (!biome.equals("ruins")) {  // Already added to ruins above
				its.registerItemGenerator(biome, 1, (g, r) -> 
					new ItemTemplateSystem.ItemData("a wooden chest", List.of(is.TAG_CONTAINER)));
			}
			if (!biome.equals("forest")) {  // Already added to forest above
				its.registerItemGenerator(biome, 1, (g, r) -> 
					new ItemTemplateSystem.ItemData("a rusty axe", List.of(is.TAG_TOOL, is.TAG_CUT)));
			}
		}
		
		log.log("Registered item generators for all biomes");
	}
	
	private void registerLandmarks() {
		LandmarkTemplateSystem lts = game.getSystem(LandmarkTemplateSystem.class);
		
		// Great Trees - visible from distance 5.0
		lts.registerLandmarkType("great_tree", 5, 5.0, r -> {
			String[] variants = {
				"an ancient oak",
				"a towering pine",
				"a massive redwood",
				"a gnarled willow",
				"a weathered elm"
			};
			return variants[r.nextInt(variants.length)];
		});
		
		// Ruined Towers - visible from distance 6.0
		lts.registerLandmarkType("ruined_tower", 3, 6.0, r -> {
			String[] variants = {
				"a crumbling tower",
				"a broken spire",
				"a ruined watchtower",
				"a collapsed fortress",
				"a shattered keep"
			};
			return variants[r.nextInt(variants.length)];
		});
		
		log.log("Registered 2 landmark types");
	}
	
	private void registerItemDescriptions() {
		ItemDescriptionSystem ids = game.getSystem(ItemDescriptionSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		// Register descriptions for common item tags
		ids.registerTagDescription(is.TAG_INFINITE_RESOURCE, "This resource is abundant here.");
		ids.registerTagDescription(is.TAG_CONTAINER, "It can hold other items.");
		ids.registerTagDescription(is.TAG_TOOL, "This appears to be a tool.");
		ids.registerTagDescription(is.TAG_CUT, "It looks sharp enough to cut things.");
		ids.registerTagDescription(is.TAG_CUTTABLE, "It could be cut down.");
		ids.registerTagDescription(is.TAG_TOY, "It looks like a toy.");
		
		log.log("Registered item tag descriptions");
	}
}
