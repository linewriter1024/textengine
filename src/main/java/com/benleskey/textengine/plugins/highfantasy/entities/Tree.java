package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.plugins.highfantasy.Cuttable;
import com.benleskey.textengine.systems.BroadcastSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
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
	
	// Command and broadcast constants
	public static final String CMD_CUT_TREE = "cut_tree";
	public static final String BROADCAST_CUTS_TREE = "actor_cuts_tree";
	// Error codes
	private static final String ERR_TREE_NOWHERE = "tree_nowhere";
	
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
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			return CommandOutput.make(CMD_CUT_TREE)
				.put(CommandOutput.M_ERROR, ERR_TREE_NOWHERE)
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
		
		// Get actor description for broadcast
		String actorDesc = eds.getSimpleDescription(actor, ws.getCurrentTime(), "someone");
		
		// Broadcast to all entities in location (using new markup system)
		// The broadcast is the only output - no separate client-specific return value
		CommandOutput broadcast = CommandOutput.make(BROADCAST_CUTS_TREE)
			.put(EntitySystem.M_ACTOR_ID, actor.getKeyId())
			.put(EntitySystem.M_ACTOR_NAME, actorDesc)
			.put(ItemSystem.M_ITEM_ID, tool.getKeyId())
			.put(ItemSystem.M_ITEM_NAME, toolName)
			.put(RelationshipSystem.M_TARGET, this.getKeyId())
			.put("target_name", targetName)
			.put("wood_id", wood.getKeyId())
			.text(Markup.concat(
				Markup.capital(Markup.entity(actor.getKeyId(), actorDesc)),
				Markup.raw(" "),
				Markup.verb("swing", "swings"),
				Markup.raw(" "),
				Markup.em(toolName),
				Markup.raw(" at "),
				Markup.em(targetName),
				Markup.raw(". After some effort, "),
				Markup.entity(actor.getKeyId(), actorDesc),
				Markup.raw(" "),
				Markup.verb("cut", "cuts"),
				Markup.raw(" it down and "),
				Markup.verb("produce", "produces"),
				Markup.raw(" "),
				Markup.em("a piece of wood"),
				Markup.raw(".")
			));
		
		bs.broadcast(actor, broadcast);
		// Return broadcast - no longer client-specific
		return broadcast;
	}
}
