package com.benleskey.textengine.plugins.games.highfantasy.entities;

import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.exceptions.InternalException;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.plugins.games.highfantasy.Cuttable;
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
 * Implements Cuttable to define what happens when cut (spawn wood, remove
 * self).
 */
public class Tree extends Item implements Cuttable {

	// Command and broadcast constants
	public static final String CMD_CUT_TREE = "cut_tree";
	public static final String BROADCAST_CUTS_TREE = "actor_cuts_tree";

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
	 * @param game   The game instance
	 * @param random Random instance for selecting description variant
	 * @return The created and configured tree entity
	 */
	public static Tree create(Game game, Random random) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);

		String description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)];

		Tree tree = es.add(Tree.class);
		ls.addLook(tree, ls.LOOK_BASIC, description);
		is.addTag(tree, is.TAG_CUTTABLE);
		is.addTag(tree, is.TAG_TAKEABLE);
		is.addTag(tree, is.TAG_WEIGHT, 50000); // 50kg - too heavy to carry

		return tree;
	}

	@Override
	public boolean onCut(Entity actor, Entity tool) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EventSystem evs = game.getSystem(EventSystem.class);
		BroadcastSystem bs = game.getSystem(BroadcastSystem.class);
		EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);

		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			throw new InternalException(
					String.format("Tree %s is nowhere, and cannot be cut down by %s using %s", this, actor, tool));
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Generate wood item using Wood.create() for proper tags
		// Use deterministic random based on tree entity ID for consistent generation
		Random random = new Random(this.getId());
		Wood wood = Wood.create(game, random);

		// Add wood to location
		rs.add(currentLocation, wood, rs.rvContains);

		// Remove this tree (cancel its containment event)
		var treeContainment = rs.getProvidingRelationships(this, rs.rvContains, ws.getCurrentTime());
		if (!treeContainment.isEmpty()) {
			evs.cancelEventsByTypeAndReference(rs.etEntityRelationship, treeContainment.get(0).getRelationship(),
					ws.getCurrentTime());
		}

		// Get actor description for broadcast
		String actorDesc = eds.getSimpleDescription(actor, ws.getCurrentTime(), "someone");
		String toolName = eds.getSimpleDescription(tool, ws.getCurrentTime());
		String targetName = eds.getSimpleDescription(this, ws.getCurrentTime());

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
						Markup.raw(".")));

		bs.broadcast(actor, broadcast);
		return true;
	}
}
