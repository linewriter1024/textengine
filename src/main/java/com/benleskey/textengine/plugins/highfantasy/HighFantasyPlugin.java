package com.benleskey.textengine.plugins.highfantasy;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.plugins.highfantasy.entities.*;
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
		
		// Register the wait command (game-specific calendar implementation)
		game.registerPlugin(new WaitCommandPlugin(game));
		
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
		// Register tag-based interactions (cutting trees with axes)
		registerTagInteractions();
		// Register item descriptions (needs ItemSystem tags initialized)
		registerItemDescriptions();
		log.log("High fantasy setup complete");
	}
	
	private void registerEntityTypes() {
		EntitySystem es = game.getSystem(EntitySystem.class);
		
		// Register consolidated entity types
		es.registerEntityType(Rock.class);    // All stone/rock items
		es.registerEntityType(Plant.class);   // All plant items (grass, flowers, mushrooms, etc.)
		es.registerEntityType(Wood.class);    // All wood items (branches, driftwood, roots)
		
		// Register specialized entity types with unique behavior
		es.registerEntityType(Axe.class);
		es.registerEntityType(Tree.class);
		es.registerEntityType(Rattle.class);
		es.registerEntityType(WoodenChest.class);
		es.registerEntityType(AncientCoin.class);
		es.registerEntityType(RustySword.class);
		es.registerEntityType(TarnishedHelmet.class);
		es.registerEntityType(WeatheredScroll.class);
		es.registerEntityType(Timepiece.class);
		es.registerEntityType(GrandfatherClock.class);
		
		log.log("Registered 13 high fantasy entity types");
	}
	
	/**
	 * Register tag-based interactions for high fantasy items.
	 * This defines what happens when TAG_CUT is used on TAG_CUTTABLE.
	 */
	private void registerTagInteractions() {
		TagInteractionSystem tis = game.getSystem(TagInteractionSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		// Register cutting interaction: TAG_CUT + TAG_CUTTABLE
		tis.registerInteraction(is.TAG_CUT, is.TAG_CUTTABLE, (client, actor, tool, toolName, target, targetName) -> {
			// If target implements Cuttable interface, delegate to it
			if (target instanceof Cuttable cuttable) {
				return cuttable.onCut(client, actor, tool, toolName, targetName);
			}
			
			// Default behavior if target doesn't implement Cuttable
			return com.benleskey.textengine.commands.CommandOutput.make("use")
				.put("success", true)
				.put("item", tool.getKeyId())
				.put("target", target.getKeyId())
				.text(com.benleskey.textengine.util.Markup.concat(
					com.benleskey.textengine.util.Markup.raw("You use "),
					com.benleskey.textengine.util.Markup.em(toolName),
					com.benleskey.textengine.util.Markup.raw(" on "),
					com.benleskey.textengine.util.Markup.em(targetName),
					com.benleskey.textengine.util.Markup.raw(".")
				));
		});
		
		log.log("Registered cutting interaction (TAG_CUT + TAG_CUTTABLE)");
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
		
		// Forest items
		its.registerItemGenerator("forest", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData(Wood::create));
		its.registerItemGenerator("forest", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		its.registerItemGenerator("forest", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		// Trees (can be cut down with axe)
		its.registerItemGenerator("forest", 4, (g, r) -> 
			new ItemTemplateSystem.ItemData(Tree::create));
		// Axes (tools for cutting trees)
		its.registerItemGenerator("forest", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData(Axe::create));
		
		// Meadow items
		its.registerItemGenerator("meadow", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		its.registerItemGenerator("meadow", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		its.registerItemGenerator("meadow", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(Rock::create));
		// Toy rattles (make sound when used)
		its.registerItemGenerator("meadow", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData(Rattle::create));
		
		// River items
		its.registerItemGenerator("river", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData(Rock::create));
		its.registerItemGenerator("river", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData(Wood::create));
		its.registerItemGenerator("river", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		
		// Hills items
		its.registerItemGenerator("hills", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData(Rock::create));
		its.registerItemGenerator("hills", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData(Plant::create));
		its.registerItemGenerator("hills", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(Wood::create));
		
		// Ruins items
		its.registerItemGenerator("ruins", 5, (g, r) -> 
			new ItemTemplateSystem.ItemData(Rock::create));
		its.registerItemGenerator("ruins", 4, (g, r) -> 
			new ItemTemplateSystem.ItemData(AncientCoin::create));
		its.registerItemGenerator("ruins", 3, (g, r) -> 
			new ItemTemplateSystem.ItemData(RustySword::create));
		its.registerItemGenerator("ruins", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(TarnishedHelmet::create));
		its.registerItemGenerator("ruins", 1, (g, r) -> 
			new ItemTemplateSystem.ItemData(WeatheredScroll::create));
		// Wooden chests (containers - can hold items)
		its.registerItemGenerator("ruins", 2, (g, r) -> 
			new ItemTemplateSystem.ItemData(WoodenChest::create));
		// Grandfather clocks (tickable items that show time)
		its.registerItemGenerator("ruins", 100, (g, r) -> 
			new ItemTemplateSystem.ItemData(GrandfatherClock::create));
		
		// Add chests and axes to all biomes (low probability)
		for (String biome : List.of("forest", "meadow", "river", "hills", "ruins")) {
			if (!biome.equals("ruins")) {  // Already added to ruins above
				its.registerItemGenerator(biome, 1, (g, r) -> 
					new ItemTemplateSystem.ItemData(WoodenChest::create));
			}
			if (!biome.equals("forest")) {  // Already added to forest above
				its.registerItemGenerator(biome, 1, (g, r) -> 
					new ItemTemplateSystem.ItemData(Axe::create));
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
