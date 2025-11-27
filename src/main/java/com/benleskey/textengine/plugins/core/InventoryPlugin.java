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
import com.benleskey.textengine.model.VisibilityDescriptor;
import com.benleskey.textengine.systems.*;
import com.benleskey.textengine.util.FuzzyMatcher;
import com.benleskey.textengine.util.Markup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * InventoryPlugin handles item interaction: taking, dropping, and examining items.
 */
public class InventoryPlugin extends Plugin implements OnPluginInitialize {
	public static final String TAKE = "take";
	public static final String TAKE_ITEM = "take_item";
	public static final String DROP = "drop";
	public static final String DROP_ITEM = "drop_item";
	public static final String INVENTORY = "inventory";
	public static final String EXAMINE = "examine";
	public static final String EXAMINE_ITEM = "examine_item";
	
	public static final String M_ITEM_NAME = "item_name";
	public static final String M_ITEM = "item";
	public static final String M_SUCCESS = "success";
	public static final String M_ERROR = "error";
	
	public InventoryPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EntityPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		// Register commands
		game.registerCommand(new Command(TAKE, this::handleTake,
			new CommandVariant(TAKE_ITEM, "^(?:take|get|pick up|pickup|grab)\\s+(.+?)\\s*$", this::parseTake)
		));
		
		game.registerCommand(new Command(DROP, this::handleDrop,
			new CommandVariant(DROP_ITEM, "^(?:drop|put down|leave)\\s+(.+?)\\s*$", this::parseDrop)
		));
		
		game.registerCommand(new Command(INVENTORY, this::handleInventory,
			new CommandVariant(INVENTORY, "^(?:inventory|inv|i)\\s*$", m -> CommandInput.makeNone())
		));
		
		game.registerCommand(new Command(EXAMINE, this::handleExamine,
			new CommandVariant(EXAMINE_ITEM, "^(?:examine|x|inspect|check)\\s+(.+?)\\s*$", this::parseExamine)
		));
	}
	
	private CommandInput parseTake(Matcher matcher) {
		String itemName = matcher.group(1).trim().toLowerCase();
		return CommandInput.makeNone().put(M_ITEM_NAME, itemName);
	}
	
	private CommandInput parseDrop(Matcher matcher) {
		String itemName = matcher.group(1).trim().toLowerCase();
		return CommandInput.makeNone().put(M_ITEM_NAME, itemName);
	}
	
	private CommandInput parseExamine(Matcher matcher) {
		String itemName = matcher.group(1).trim().toLowerCase();
		return CommandInput.makeNone().put(M_ITEM_NAME, itemName);
	}
	
	/**
	 * Handle the "take" command - pick up an item from the current location.
	 */
	private void handleTake(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		Optional<Object> itemNameOpt = input.getO(M_ITEM_NAME);
		if (itemNameOpt.isEmpty()) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_ERROR, "no_item")
				.text(Markup.escape("What do you want to take?")));
			return;
		}
		
		String itemName = itemNameOpt.get().toString();
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Find items in current location
		List<RelationshipDescriptor> itemsInLocation = rs.getReceivingRelationships(
			currentLocation, rs.rvContains, ws.getCurrentTime()
		);
		
		// Filter to actual Item entities
		List<Entity> items = itemsInLocation.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Check if itemName is a numeric ID first
		Entity foundItem = null;
		try {
			int numericId = Integer.parseInt(itemName);
			Optional<Entity> entityById = client.getEntityByNumericId(numericId);
			if (entityById.isPresent() && items.contains(entityById.get())) {
				foundItem = entityById.get();
			}
		} catch (NumberFormatException e) {
			// Not a number, will do fuzzy match below
		}
		
		// If not found by numeric ID, do fuzzy matching
		if (foundItem == null) {
			foundItem = FuzzyMatcher.match(itemName, items, item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return looks.isEmpty() ? null : looks.get(0).getDescription();
			});
		}
		
		final Entity targetItem = foundItem;
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_ERROR, "not_found")
				.text(Markup.escape("You don't see that here.")));
			return;
		}
		
		// Remove item from location and add to actor's inventory
		// Cancel the old containment relationship
		RelationshipDescriptor oldRelationship = itemsInLocation.stream()
			.filter(rd -> rd.getReceiver().equals(targetItem))
			.findFirst()
			.orElse(null);
		
		if (oldRelationship != null) {
			game.getSystem(EventSystem.class).cancelEvent(oldRelationship.getRelationship());
		}
		
		// Create new containment relationship with actor
		rs.add(actor, targetItem, rs.rvContains);
		
		// Get item description for output
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemDescription = looks.isEmpty() ? "something" : Markup.toPlainText(Markup.raw(looks.get(0).getDescription()));
		
		client.sendOutput(CommandOutput.make(TAKE)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem)
			.put(M_ITEM_NAME, itemDescription)
			.text(Markup.escape("You take " + itemDescription + ".")));
	}
	
	/**
	 * Handle the "drop" command - drop an item from inventory.
	 */
	private void handleDrop(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		Optional<Object> itemNameOpt = input.getO(M_ITEM_NAME);
		if (itemNameOpt.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_ERROR, "no_item")
				.text(Markup.escape("What do you want to drop?")));
			return;
		}
		
		String itemName = itemNameOpt.get().toString();
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Find items in actor's inventory
		List<RelationshipDescriptor> itemsInInventory = rs.getReceivingRelationships(
			actor, rs.rvContains, ws.getCurrentTime()
		);
		
		// Filter to actual Item entities and find match
		List<Entity> items = itemsInInventory.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Try numeric ID first, then fuzzy match
		Entity foundItem = null;
		try {
			int numericId = Integer.parseInt(itemName);
			foundItem = client.getEntityByNumericId(numericId).orElse(null);
			// Verify the entity is actually in inventory
			if (foundItem != null && !items.contains(foundItem)) {
				foundItem = null;
			}
		} catch (NumberFormatException e) {
			// Not a number, try fuzzy matching
			foundItem = FuzzyMatcher.match(itemName, items, item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return looks.isEmpty() ? null : looks.get(0).getDescription();
			});
		}
		
		final Entity targetItem = foundItem;
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_ERROR, "not_found")
				.text(Markup.escape("You don't have that.")));
			return;
		}
		
		// Find current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Remove item from actor's inventory and add to location
		RelationshipDescriptor oldRelationship = itemsInInventory.stream()
			.filter(rd -> rd.getReceiver().equals(targetItem))
			.findFirst()
			.orElse(null);
		
		if (oldRelationship != null) {
			game.getSystem(EventSystem.class).cancelEvent(oldRelationship.getRelationship());
		}
		
		// Create new containment relationship with location
		rs.add(currentLocation, targetItem, rs.rvContains);
		
		// Get item description for output
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemDescription = looks.isEmpty() ? "something" : Markup.toPlainText(Markup.raw(looks.get(0).getDescription()));
		
		client.sendOutput(CommandOutput.make(DROP)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem)
			.put(M_ITEM_NAME, itemDescription)
			.text(Markup.escape("You drop " + itemDescription + ".")));
	}
	
	/**
	 * Handle the "inventory" command - list items the actor is carrying.
	 */
	private void handleInventory(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		
		// Find items in actor's inventory
		List<RelationshipDescriptor> itemsInInventory = rs.getReceivingRelationships(
			actor, rs.rvContains, ws.getCurrentTime()
		);
		
		// Filter to actual Item entities
		List<Entity> items = itemsInInventory.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (items.isEmpty()) {
			client.sendOutput(CommandOutput.make(INVENTORY)
				.text(Markup.escape("You are not carrying anything.")));
			return;
		}
		
		// Build inventory list
		StringBuilder inventoryText = new StringBuilder("You are carrying:");
		for (Entity item : items) {
			List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			String description = looks.isEmpty() ? "something" : looks.get(0).getDescription();
			inventoryText.append("\n  - ").append(description);
		}
		
		client.sendOutput(CommandOutput.make(INVENTORY)
			.put("items", items)
			.text(Markup.escape(inventoryText.toString())));
	}
	
	/**
	 * Handle the "examine" command - look closely at an item.
	 */
	private void handleExamine(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		Optional<Object> itemNameOpt = input.getO(M_ITEM_NAME);
		if (itemNameOpt.isEmpty()) {
			client.sendOutput(CommandOutput.make(EXAMINE)
				.put(M_ERROR, "no_item")
				.text(Markup.escape("What do you want to examine?")));
			return;
		}
		
		String itemName = itemNameOpt.get().toString();
		
		WorldSystem ws = game.getSystem(WorldSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		VisibilitySystem vs = game.getSystem(VisibilitySystem.class);
		
		// Get visible entities (items in inventory + items in location)
		List<VisibilityDescriptor> visibleDescriptors = vs.getVisibleEntities(actor);
		List<Entity> visibleItems = visibleDescriptors.stream()
			.map(VisibilityDescriptor::getEntity)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Try numeric ID first, then fuzzy match
		Entity foundItem = null;
		try {
			int numericId = Integer.parseInt(itemName);
			foundItem = client.getEntityByNumericId(numericId).orElse(null);
			// Verify the entity is actually visible
			if (foundItem != null && !visibleItems.contains(foundItem)) {
				foundItem = null;
			}
		} catch (NumberFormatException e) {
			// Not a number, try fuzzy matching
			foundItem = FuzzyMatcher.match(itemName, visibleItems, item -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return looks.isEmpty() ? null : looks.get(0).getDescription();
			});
		}
		
		final Entity targetItem = foundItem;
		
		if (targetItem == null) {
			client.sendOutput(CommandOutput.make(EXAMINE)
				.put(M_ERROR, "not_found")
				.text(Markup.escape("You don't see that.")));
			return;
		}
		
		// Build detailed description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String description = looks.isEmpty() ? "something" : looks.get(0).getDescription();
		
		List<Markup.Safe> examineMarkup = new ArrayList<>();
		examineMarkup.add(Markup.raw("You examine "));
		examineMarkup.add(Markup.em(Markup.toPlainText(Markup.raw(description))));
		examineMarkup.add(Markup.raw("."));
		
		// Add tag-based descriptions
		ItemDescriptionSystem ids = game.getSystem(ItemDescriptionSystem.class);
		List<String> tagDescriptions = ids.getDescriptions(targetItem, ws.getCurrentTime());
		if (!tagDescriptions.isEmpty()) {
			examineMarkup.add(Markup.raw("\n"));
			for (String tagDesc : tagDescriptions) {
				examineMarkup.add(Markup.raw(tagDesc));
				examineMarkup.add(Markup.raw("\n"));
			}
		}
		
		// Add weight if present
		ItemSystem is = game.getSystem(ItemSystem.class);
		Long weight = is.getTagValue(targetItem, is.TAG_WEIGHT, ws.getCurrentTime());
		if (weight != null) {
			examineMarkup.add(Markup.raw("Weight: " + weight + "\n"));
		}
		
		client.sendOutput(CommandOutput.make(EXAMINE)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem)
			.text(Markup.concat(examineMarkup.toArray(new Markup.Safe[0]))));
	}
}
