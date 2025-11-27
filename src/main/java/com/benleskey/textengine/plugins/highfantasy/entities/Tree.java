package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Client;
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

/**
 * A tree that can be cut down to produce wood.
 * Implements Cuttable to define what happens when cut (spawn wood, remove self).
 */
public class Tree extends Item implements Cuttable {
	
	public Tree(long id, Game game) {
		super(id, game);
	}
	
	/**
	 * Create a tree with the specified description.
	 * Adds basic look and TAG_CUTTABLE automatically.
	 * 
	 * @param game The game instance
	 * @param description The description of the tree (e.g., "a tree", "an oak tree")
	 * @return The created and configured tree entity
	 */
	public static Tree create(Game game, String description) {
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		
		Tree tree = es.add(Tree.class);
		ls.addLook(tree, "basic", description);
		is.addTag(tree, is.TAG_CUTTABLE);
		
		return tree;
	}
	
	@Override
	public CommandOutput onCut(Client client, Entity actor, Entity tool, String toolName, String targetName) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
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
		
		// Generate wood item
		Item wood = es.add(Item.class);
		ls.addLook(wood, "basic", "a piece of wood");
		
		// Add wood to location
		rs.add(currentLocation, wood, rs.rvContains);
		
		// Remove this tree (cancel its containment)
		var treeContainment = rs.getProvidingRelationships(this, rs.rvContains, ws.getCurrentTime());
		if (!treeContainment.isEmpty()) {
			evs.cancelEvent(treeContainment.get(0).getRelationship());
		}
		
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
