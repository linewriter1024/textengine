package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.plugins.highfantasy.Cuttable;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.EventSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Random;

/**
 * A tree that can be cut down to produce wood.
 * Implements Cuttable to define what happens when cut (spawn wood, remove self).
 */
public class Tree extends Item implements Cuttable {
	
	private static final String[] DESCRIPTIONS = {
		"a tree",
		"an oak tree",
		"a pine tree",
		"a birch tree"
	};
	
	public Tree(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a tree with a randomly selected description variant.
	 * 
	 * @param game The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured tree entity
	 */
	public static Tree create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];
		
		Tree tree = es.add(Tree.class);
		ls.addLook(tree, "basic", description);
		is.addTag(tree, is.TAG_CUTTABLE);
		is.addTag(tree, is.TAG_TAKEABLE);
		is.addTag(tree, is.TAG_WEIGHT, 50000); // 50kg - too heavy to carry
		
		return tree;
	}
	
	@Override
	public CommandOutput onCut(Entity actor, Entity tool, String toolName, String targetName) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EventSystem evs = game.getSystem(EventSystem.class);
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return CommandOutput.make("use")
				.put("success", false)
				.put("error", "nowhere")
				.text(Markup.escape("You are nowhere."));
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Generate wood item using Wood.create() for proper tags
		// Use deterministic random based on tree entity ID for consistent generation
		Random random = new Random(this.getId());
		Wood wood = Wood.create(game, random);
		
		// Add wood to location
		rs.add(currentLocation, wood, rs.rvContains);
		
		// Remove this tree (cancel its containment)
		var treeContainment = rs.getProvidingRelationships(this, rs.rvContains, ws.getCurrentTime());
		if (!treeContainment.isEmpty()) {
			evs.cancelEvent(treeContainment.get(0).getRelationship());
		}
		
		// Return output for the actor (broadcasting handled by TagInteractionSystem)
		return CommandOutput.make("use")
			.put("success", true)
			.put("item", tool.getKeyId())
			.put("target", this.getKeyId())
			.text(Markup.concat(
				Markup.raw("You swing "),
				Markup.em(toolName),
				Markup.raw(" at "),
				Markup.em(targetName),
				Markup.raw(". After some effort, you cut it down and produce "),
				Markup.em("a piece of wood"),
				Markup.raw(".")
			));
	}
}
