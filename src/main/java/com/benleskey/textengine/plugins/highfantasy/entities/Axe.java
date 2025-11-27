package com.benleskey.textengine.plugins.highfantasy.entities;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.UsableOnTarget;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;

/**
 * An axe that can be used to cut down cuttable things (trees), producing wood.
 * Works on any entity tagged with TAG_CUTTABLE.
 */
public class Axe extends Item implements UsableOnTarget {
	
	public Axe(long id, Game game) {
		super(id, game);
	}
	
	@Override
	public CommandOutput useOn(Client client, Entity actor, Entity target, String targetName) {
		ItemSystem is = game.getSystem(ItemSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		
		// Only works on cuttable things
		if (!is.hasTag(target, is.TAG_CUTTABLE, ws.getCurrentTime())) {
			return null; // No interaction defined
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);
		
		// Get axe description
		List<LookDescriptor> itemLooks = ls.getLooksFromEntity(this, ws.getCurrentTime());
		String itemName = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "the axe";
		
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
		
		// Remove the tree (cancel its containment)
		var treeContainment = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
		if (!treeContainment.isEmpty()) {
			game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
				.cancelEvent(treeContainment.get(0).getRelationship());
		}
		
		return CommandOutput.make("use")
			.put("success", true)
			.put("item", this.getKeyId())
			.put("target", target.getKeyId())
			.text(Markup.concat(
				Markup.raw("You swing "),
				Markup.em(itemName),
				Markup.raw(" at "),
				Markup.em(targetName),
				Markup.raw(". After some effort, you cut it down and produce "),
				Markup.em("a piece of wood"),
				Markup.raw(".")
			));
	}
}
