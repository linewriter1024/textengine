package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.RelationshipDescriptor;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * ItemInteractionPlugin handles item manipulation: take, drop, examine, use, inventory.
 */
public class ItemInteractionPlugin extends Plugin implements OnPluginInitialize {
	public static final String TAKE = "take";
	public static final String DROP = "drop";
	public static final String EXAMINE = "examine";
	public static final String USE = "use";
	public static final String INVENTORY = "inventory";
	
	public static final String M_ITEM = "item";
	public static final String M_ITEM_NAME = "item_name";
	public static final String M_SUCCESS = "success";
	public static final String M_ERROR = "error";
	public static final String M_TARGET = "target";
	public static final String M_CONTAINER = "container";
	public static final String M_ITEMS = "items";
	
	public ItemInteractionPlugin(Game game) {
		super(game);
	}
	
	@Override
	public void onPluginInitialize() {
		// Take/get item
		game.registerCommand(new Command(TAKE, this::handleTake,
			new CommandVariant("take_item", "^(?:take|get|pickup|grab)\\s+(.+?)\\s*$", this::parseTake)
		));
		
		// Drop item
		game.registerCommand(new Command(DROP, this::handleDrop,
			new CommandVariant("drop_item", "^drop\\s+(.+?)\\s*$", this::parseDrop)
		));
		
		// Examine item (detailed look)
		game.registerCommand(new Command(EXAMINE, this::handleExamine,
			new CommandVariant("examine_item", "^(?:examine|inspect|x)\\s+(.+?)\\s*$", this::parseExamine)
		));
		
		// Use item (or use item on target)
		// NOTE: Variants stored in HashMap (no guaranteed order), so regexes must be mutually exclusive
		// "use_on" matches: "use X on Y" or "use X with Y"
		// "use_item" matches: "use X" (but NOT if it contains "on" or "with" followed by more words)
		game.registerCommand(new Command(USE, this::handleUse,
			new CommandVariant("use_on", "^use\\s+(.+?)\\s+(?:on|with)\\s+(.+?)\\s*$", this::parseUseOn),
			new CommandVariant("use_item", "^use\\s+(?!.+?\\s+(?:on|with)\\s+)(.+?)\\s*$", this::parseUse)
		));
		
		// Inventory
		game.registerCommand(new Command(INVENTORY, this::handleInventory,
			new CommandVariant("inventory", "^(?:inventory|inv|i)\\s*$", args -> CommandInput.makeNone())
		));
	}
	
	private CommandInput parseTake(Matcher matcher) {
		return CommandInput.makeNone().put(M_ITEM, matcher.group(1).trim());
	}
	
	private CommandInput parseDrop(Matcher matcher) {
		return CommandInput.makeNone().put(M_ITEM, matcher.group(1).trim());
	}
	
	private CommandInput parseExamine(Matcher matcher) {
		return CommandInput.makeNone().put(M_ITEM, matcher.group(1).trim());
	}
	
	private CommandInput parseUse(Matcher matcher) {
		return CommandInput.makeNone().put(M_ITEM, matcher.group(1).trim());
	}
	
	private CommandInput parseUseOn(Matcher matcher) {
		return CommandInput.makeNone()
			.put(M_ITEM, matcher.group(1).trim())
			.put(M_TARGET, matcher.group(2).trim());
	}
	
	private void handleTake(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get items at current location (not carried by actor)
		List<Entity> itemsHere = rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Resolve which item the player wants
		String itemInput = input.get(M_ITEM);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		Entity targetItem = ds.resolveEntity(
			client,
			itemInput,
			itemsHere,
			item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(itemInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Remove item from location, add to actor's inventory
		var oldContainment = rs.getProvidingRelationships(targetItem, rs.rvContains, ws.getCurrentTime());
		if (!oldContainment.isEmpty()) {
			game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
				.cancelEvent(oldContainment.get(0).getRelationship());
		}
		
		rs.add(actor, targetItem, rs.rvContains);
		
		client.sendOutput(CommandOutput.make(TAKE)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem.getKeyId())
			.put(M_ITEM_NAME, itemName)
			.text(Markup.concat(
				Markup.raw("You take "),
				Markup.em(itemName),
				Markup.raw(".")
			)));
	}
	
	private void handleDrop(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get items carried by actor
		List<Entity> carriedItems = rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "empty_inventory")
				.text(Markup.escape("You aren't carrying anything.")));
			return;
		}
		
		// Resolve which item to drop
		String itemInput = input.get(M_ITEM);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		Entity targetItem = ds.resolveEntity(
			client,
			itemInput,
			carriedItems,
			item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_carrying")
				.text(Markup.concat(
					Markup.raw("You aren't carrying "),
					Markup.em(itemInput),
					Markup.raw(".")
				)));
			return;
		}
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Remove from actor, add to location
		var oldContainment = rs.getProvidingRelationships(targetItem, rs.rvContains, ws.getCurrentTime());
		if (!oldContainment.isEmpty()) {
			game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
				.cancelEvent(oldContainment.get(0).getRelationship());
		}
		
		rs.add(currentLocation, targetItem, rs.rvContains);
		
		client.sendOutput(CommandOutput.make(DROP)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem.getKeyId())
			.put(M_ITEM_NAME, itemName)
			.text(Markup.concat(
				Markup.raw("You drop "),
				Markup.em(itemName),
				Markup.raw(".")
			)));
	}
	
	private void handleExamine(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Get items from both inventory and current location
		List<Entity> availableItems = new java.util.ArrayList<>();
		
		// Carried items
		availableItems.addAll(rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		
		// Items at location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (!containers.isEmpty()) {
			Entity currentLocation = containers.get(0).getProvider();
			availableItems.addAll(rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));
		}
		
		// Resolve which item to examine
		String itemInput = input.get(M_ITEM);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		Entity targetItem = ds.resolveEntity(
			client,
			itemInput,
			availableItems,
			item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(EXAMINE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(itemInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		// Get item description and details
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Check if it's a container with contents
		List<Entity> contents = rs.getReceivingRelationships(targetItem, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
		parts.add(Markup.concat(
			Markup.raw("You examine "),
			Markup.em(itemName),
			Markup.raw(".")
		));
		
		// Show detailed description if available
		if (!looks.isEmpty()) {
			parts.add(Markup.concat(
				Markup.raw(" "),
				Markup.escape(looks.get(0).getDescription())
			));
		}
		
		// If it's a container, show contents
		if (!contents.isEmpty()) {
			parts.add(Markup.raw(" It contains: "));
			
			DisambiguationSystem.DisambiguatedList contentList = ds.buildDisambiguatedList(
				contents,
				item -> {
					List<LookDescriptor> itemLooks = ls.getLooksFromEntity(item, ws.getCurrentTime());
					return !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : null;
				}
			);
			
			List<Markup.Safe> contentParts = contentList.getMarkupParts();
			for (int i = 0; i < contentParts.size(); i++) {
				if (i > 0) {
					if (i == contentParts.size() - 1) {
						parts.add(Markup.raw(", and "));
					} else {
						parts.add(Markup.raw(", "));
					}
				}
				parts.add(contentParts.get(i));
			}
			parts.add(Markup.raw("."));
		}
		
		client.sendOutput(CommandOutput.make(EXAMINE)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem.getKeyId())
			.put(M_ITEM_NAME, itemName)
			.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}
	
	private void handleUse(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Get carried items (can only use what you're carrying)
		List<Entity> carriedItems = rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "empty_inventory")
				.text(Markup.escape("You aren't carrying anything to use.")));
			return;
		}
		
		// Resolve which item to use
		String itemInput = input.get(M_ITEM);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		Entity targetItem = ds.resolveEntity(
			client,
			itemInput,
			carriedItems,
			item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_carrying")
				.text(Markup.concat(
					Markup.raw("You aren't carrying "),
					Markup.em(itemInput),
					Markup.raw(".")
				)));
			return;
		}
		
		// Check if using on a target
		java.util.Optional<Object> targetOpt = input.getO(M_TARGET);
		
		if (targetOpt.isPresent()) {
			// Using item on target - delegate to ItemActionSystem
			handleUseOn(client, actor, targetItem, targetOpt.get().toString());
		} else {
			// Using item by itself
			handleUseSolo(client, actor, targetItem);
		}
	}
	
	private void handleUseSolo(Client client, Entity actor, Entity item) {
		LookSystem ls = game.getSystem(LookSystem.class);
		List<LookDescriptor> looks = ls.getLooksFromEntity(item, game.getSystem(WorldSystem.class).getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Check if this is a rattle (toy that makes sound)
		if (itemName.contains("rattle")) {
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, true)
				.put(M_ITEM, item.getKeyId())
				.put(M_ITEM_NAME, itemName)
				.text(Markup.concat(
					Markup.raw("You shake "),
					Markup.em(itemName),
					Markup.raw(". It makes a pleasant rattling sound.")
				)));
			return;
		}
		
		// Default: item has no solo use
		client.sendOutput(CommandOutput.make(USE)
			.put(M_SUCCESS, false)
			.put(M_ERROR, "no_use")
			.text(Markup.concat(
				Markup.raw("You're not sure how to use "),
				Markup.em(itemName),
				Markup.raw(" by itself.")
			)));
	}
	
	private void handleUseOn(Client client, Entity actor, Entity item, String targetInput) {
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		EntitySystem es = game.getSystem(EntitySystem.class);
		
		// Get item description
		List<LookDescriptor> itemLooks = ls.getLooksFromEntity(item, ws.getCurrentTime());
		String itemName = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "the item";
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get available targets (items at location or nearby entities)
		List<Entity> availableTargets = new java.util.ArrayList<>();
		
		// Items at location
		availableTargets.addAll(rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.collect(Collectors.toList()));
		
		// Resolve target
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		Entity target = ds.resolveEntity(
			client,
			targetInput,
			availableTargets,
			entity -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(entity, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		if (target == null) {
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "target_not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(targetInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		// Get target description
		List<LookDescriptor> targetLooks = ls.getLooksFromEntity(target, ws.getCurrentTime());
		String targetName = !targetLooks.isEmpty() ? targetLooks.get(0).getDescription() : "the target";
		
		// Check for axe + tree interaction
		if (itemName.contains("axe") && targetName.contains("tree")) {
			// Generate wood item
			Item wood = es.add(Item.class);
			ls.addLook(wood, "basic", "a piece of wood");
			
			// Add wood to location
			rs.add(currentLocation, wood, rs.rvContains);
			
			client.sendOutput(CommandOutput.make(USE)
				.put(M_SUCCESS, true)
				.put(M_ITEM, item.getKeyId())
				.put(M_TARGET, target.getKeyId())
				.text(Markup.concat(
					Markup.raw("You swing "),
					Markup.em(itemName),
					Markup.raw(" at "),
					Markup.em(targetName),
					Markup.raw(". After some effort, you cut it down and produce "),
					Markup.em("a piece of wood"),
					Markup.raw(".")
				)));
			
			// Remove the tree
			var treeContainment = rs.getProvidingRelationships(target, rs.rvContains, ws.getCurrentTime());
			if (!treeContainment.isEmpty()) {
				game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
					.cancelEvent(treeContainment.get(0).getRelationship());
			}
			return;
		}
		
		// Default: no interaction defined
		client.sendOutput(CommandOutput.make(USE)
			.put(M_SUCCESS, false)
			.put(M_ERROR, "no_interaction")
			.text(Markup.concat(
				Markup.raw("You can't use "),
				Markup.em(itemName),
				Markup.raw(" on "),
				Markup.em(targetName),
				Markup.raw(".")
			)));
	}
	
	private void handleInventory(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Get carried items
		List<Entity> carriedItems = rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(INVENTORY)
				.put(M_SUCCESS, true)
				.put(M_ITEMS, new java.util.ArrayList<String>())
				.text(Markup.escape("You aren't carrying anything.")));
			return;
		}
		
		// Build disambiguated list
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		DisambiguationSystem.DisambiguatedList itemList = ds.buildDisambiguatedList(
			carriedItems,
			item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			}
		);
		
		// Format output
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
		parts.add(Markup.raw("You are carrying: "));
		
		List<Markup.Safe> itemParts = itemList.getMarkupParts();
		for (int i = 0; i < itemParts.size(); i++) {
			if (i > 0) {
				if (i == itemParts.size() - 1) {
					parts.add(Markup.raw(", and "));
				} else {
					parts.add(Markup.raw(", "));
				}
			}
			parts.add(itemParts.get(i));
		}
		parts.add(Markup.raw("."));
		
		// Update client's numeric ID map
		client.setNumericIdMap(itemList.getNumericIdMap());
		
		client.sendOutput(CommandOutput.make(INVENTORY)
			.put(M_SUCCESS, true)
			.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}
}
