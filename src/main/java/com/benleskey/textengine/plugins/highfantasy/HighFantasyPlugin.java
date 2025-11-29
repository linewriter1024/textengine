package com.benleskey.textengine.plugins.highfantasy;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.plugins.highfantasy.entities.*;
import com.benleskey.textengine.plugins.procgen1.BiomeSystem;
import com.benleskey.textengine.plugins.procgen1.systems.ItemTemplateSystem;
import com.benleskey.textengine.plugins.procgen1.systems.LandmarkTemplateSystem;
import com.benleskey.textengine.plugins.procgen1.systems.PlaceDescriptionSystem;
import com.benleskey.textengine.systems.*;

import java.util.*;

/**
 * HighFantasyPlugin registers fantasy-themed content for procedural world
 * generation.
 * This includes biomes (forests, meadows, ruins), items (swords, coins), and
 * landmarks.
 * 
 * Separated from ProceduralWorldPlugin to demonstrate genre-specific content
 * registration.
 */
public class HighFantasyPlugin extends Plugin implements OnPluginInitialize, OnCoreSystemsReady {

	public static final String NAME_STYLE_DEFAULT = "highfantasy_default";

	public HighFantasyPlugin(Game game) {
		super(game);
	}

	// System fields
	private EntitySystem entitySystem;
	private TagInteractionSystem tagInteractionSystem;
	private ItemSystem itemSystem;
	private BiomeSystem biomeSystem;
	private PlaceDescriptionSystem placeDescriptionSystem;
	private ItemTemplateSystem itemTemplateSystem;
	private LandmarkTemplateSystem landmarkTemplateSystem;
	private EntityDescriptionSystem entityDescriptionSystem;
	private NameGenerationSystem nameGenerationSystem;

	@Override
	public Set<Plugin> getDependencies() {
		// Ensure ProceduralWorldPlugin is loaded first so systems are registered
		return Set.of(
				game.getPlugin(com.benleskey.textengine.plugins.core.EntityPlugin.class),
				game.getPlugin(com.benleskey.textengine.plugins.procgen1.systems.ProceduralWorldPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		log.log("Registering high fantasy content...");

		// Register the wait command (game-specific calendar implementation)
		game.registerPlugin(new WaitCommandPlugin(game));

		log.log("High fantasy content registered");
	}

	@Override
	public void onCoreSystemsReady() {
		// Initialize systems
		entitySystem = game.getSystem(EntitySystem.class);
		tagInteractionSystem = game.getSystem(TagInteractionSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);
		biomeSystem = game.getSystem(BiomeSystem.class);
		placeDescriptionSystem = game.getSystem(PlaceDescriptionSystem.class);
		itemTemplateSystem = game.getSystem(ItemTemplateSystem.class);
		landmarkTemplateSystem = game.getSystem(LandmarkTemplateSystem.class);
		entityDescriptionSystem = game.getSystem(EntityDescriptionSystem.class);
		nameGenerationSystem = game.getSystem(NameGenerationSystem.class);

		log.log("Registering high fantasy entity types and content...");

		// Register content (needs systems initialized)
		registerBiomes();
		registerPlaceDescriptions();
		registerItems();
		registerLandmarks();
		registerNameStyles();

		// Register entity types (used by items generated after this point)
		registerEntityTypes();
		// Register tag-based interactions (cutting trees with axes)
		registerTagInteractions();
		// Register item descriptions (needs ItemSystem tags initialized)
		registerItemDescriptions();
		log.log("High fantasy setup complete");
	}

	private void registerNameStyles() {
		nameGenerationSystem.registerStyle(NAME_STYLE_DEFAULT,
				List.of("al", "an", "ar", "bel", "dor", "el", "en", "er", "gal", "gorn", "hel",
						"is", "or", "ul", "ur", "ri", "ta", "ven", "my", "sha", "ka", "lo", "mi", "za", "ri", "sa",
						"te"));
	}

	private void registerEntityTypes() {

		// Register consolidated entity types
		entitySystem.registerEntityType(Rock.class); // All stone/rock items
		entitySystem.registerEntityType(Plant.class); // All plant items (grass, flowers, mushrooms, etc.)
		entitySystem.registerEntityType(Wood.class); // All wood items (branches, driftwood, roots)

		// Register specialized entity types with unique behavior
		entitySystem.registerEntityType(Axe.class);
		entitySystem.registerEntityType(Tree.class);
		entitySystem.registerEntityType(Rattle.class);
		entitySystem.registerEntityType(WoodenChest.class);
		entitySystem.registerEntityType(AncientCoin.class);
		entitySystem.registerEntityType(RustySword.class);
		entitySystem.registerEntityType(TarnishedHelmet.class);
		entitySystem.registerEntityType(WeatheredScroll.class);
		entitySystem.registerEntityType(Timepiece.class);
		entitySystem.registerEntityType(GrandfatherClock.class);
		entitySystem.registerEntityType(Goblin.class);

		log.log("Registered 14 high fantasy entity types");
	}

	/**
	 * Register tag-based interactions for high fantasy items.
	 * This defines what happens when TAG_CUT is used on TAG_CUTTABLE.
	 */
	private void registerTagInteractions() {

		// Register cutting interaction: TAG_CUT + TAG_CUTTABLE
		tagInteractionSystem.registerInteraction(itemSystem.TAG_CUT, itemSystem.TAG_CUTTABLE,
				(actor, tool, target) -> {
					if (target instanceof Cuttable cuttable) {
						return cuttable.onCut(actor, tool);
					} else {
						throw new InternalException("Entity with TAG_CUTTABLE does not implement Cuttable interface: "
								+ target);
					}
				});
	}

	private void registerBiomes() {

		// Register fantasy biomes with weights
		biomeSystem.registerBiome("forest", 30);
		biomeSystem.registerBiome("meadow", 25);
		biomeSystem.registerBiome("river", 15);
		biomeSystem.registerBiome("hills", 20);
		biomeSystem.registerBiome("ruins", 10);

		log.log("Registered 5 fantasy biomes");
	}

	private void registerPlaceDescriptions() {

		// Forest descriptions
		placeDescriptionSystem.registerDescriptionGenerator("forest", 3, r -> "a dense forest");
		placeDescriptionSystem.registerDescriptionGenerator("forest", 2, r -> "a dark woodland");
		placeDescriptionSystem.registerDescriptionGenerator("forest", 2, r -> "a grove of ancient trees");
		placeDescriptionSystem.registerDescriptionGenerator("forest", 1, r -> "a misty forest path");

		// Meadow descriptions
		placeDescriptionSystem.registerDescriptionGenerator("meadow", 3, r -> "a grassy meadow");
		placeDescriptionSystem.registerDescriptionGenerator("meadow", 2, r -> "a sunlit clearing");
		placeDescriptionSystem.registerDescriptionGenerator("meadow", 2, r -> "a field of wildflowers");
		placeDescriptionSystem.registerDescriptionGenerator("meadow", 1, r -> "a peaceful glade");

		// River descriptions
		placeDescriptionSystem.registerDescriptionGenerator("river", 3, r -> "a flowing river");
		placeDescriptionSystem.registerDescriptionGenerator("river", 2, r -> "a babbling brook");
		placeDescriptionSystem.registerDescriptionGenerator("river", 2, r -> "a rushing stream");
		placeDescriptionSystem.registerDescriptionGenerator("river", 1, r -> "a calm waterway");

		// Hills descriptions
		placeDescriptionSystem.registerDescriptionGenerator("hills", 3, r -> "rolling hills");
		placeDescriptionSystem.registerDescriptionGenerator("hills", 2, r -> "a rocky hillside");
		placeDescriptionSystem.registerDescriptionGenerator("hills", 2, r -> "a windswept rise");
		placeDescriptionSystem.registerDescriptionGenerator("hills", 1, r -> "a grassy knoll");

		// Ruins descriptions
		placeDescriptionSystem.registerDescriptionGenerator("ruins", 3, r -> "ancient ruins");
		placeDescriptionSystem.registerDescriptionGenerator("ruins", 2, r -> "crumbling stonework");
		placeDescriptionSystem.registerDescriptionGenerator("ruins", 2, r -> "weathered ruins");
		placeDescriptionSystem.registerDescriptionGenerator("ruins", 1, r -> "a forgotten monument");

		log.log("Registered place descriptions for all biomes");
	}

	private void registerItems() {

		// Forest items
		itemTemplateSystem.registerItemGenerator("forest", 5, Wood::create);
		itemTemplateSystem.registerItemGenerator("forest", 3, Plant::create);
		itemTemplateSystem.registerItemGenerator("forest", 2, Plant::create);
		// Trees (can be cut down with axe)
		itemTemplateSystem.registerItemGenerator("forest", 4, Tree::create);
		// Axes (tools for cutting trees)
		itemTemplateSystem.registerItemGenerator("forest", 1, Axe::create);

		// Meadow items
		itemTemplateSystem.registerItemGenerator("meadow", 5, Plant::create);
		itemTemplateSystem.registerItemGenerator("meadow", 3, Plant::create);
		itemTemplateSystem.registerItemGenerator("meadow", 2, Rock::create);
		// Toy rattles (make sound when used)
		itemTemplateSystem.registerItemGenerator("meadow", 1, Rattle::create);

		// River items
		itemTemplateSystem.registerItemGenerator("river", 5, Rock::create);
		itemTemplateSystem.registerItemGenerator("river", 3, Wood::create);
		itemTemplateSystem.registerItemGenerator("river", 2, Plant::create);

		// Hills items
		itemTemplateSystem.registerItemGenerator("hills", 5, Rock::create);
		itemTemplateSystem.registerItemGenerator("hills", 3, Plant::create);
		itemTemplateSystem.registerItemGenerator("hills", 2, Wood::create);

		// Ruins items
		itemTemplateSystem.registerItemGenerator("ruins", 5, Rock::create);
		itemTemplateSystem.registerItemGenerator("ruins", 4, AncientCoin::create);
		itemTemplateSystem.registerItemGenerator("ruins", 3, RustySword::create);
		itemTemplateSystem.registerItemGenerator("ruins", 2, TarnishedHelmet::create);
		itemTemplateSystem.registerItemGenerator("ruins", 1, WeatheredScroll::create);
		// Wooden chests (containers - can hold items)
		itemTemplateSystem.registerItemGenerator("ruins", 2, WoodenChest::create);
		// Grandfather clocks (tickable items that show time)
		itemTemplateSystem.registerItemGenerator("ruins", 100, GrandfatherClock::create);

		// Add chests and axes to all biomes (low probability)
		for (String biome : List.of("forest", "meadow", "river", "hills", "ruins")) {
			if (!biome.equals("ruins")) { // Already added to ruins above
				itemTemplateSystem.registerItemGenerator(biome, 1, WoodenChest::create);
			}
			if (!biome.equals("forest")) { // Already added to forest above
				itemTemplateSystem.registerItemGenerator(biome, 1, Axe::create);
			}
		}

		log.log("Registered item generators for all biomes");
	}

	private void registerLandmarks() {

		// Great Trees - visible from distance 5.0
		landmarkTemplateSystem.registerLandmarkType("great_tree", 5, 5.0, r -> {
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
		landmarkTemplateSystem.registerLandmarkType("ruined_tower", 3, 6.0, r -> {
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

		// Register descriptions for common item tags
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_INFINITE_RESOURCE,
				"This resource is abundant here.");
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_CONTAINER, "It can hold other items.");
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_TOOL, "This appears to be a tool.");
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_CUT, "It looks sharp enough to cut things.");
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_CUTTABLE, "It could be cut down.");
		entityDescriptionSystem.registerTagDescription(itemSystem.TAG_TOY, "It looks like a toy.");

		log.log("Registered item tag descriptions");
	}
}
