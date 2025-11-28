package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.entities.Item;
import com.benleskey.textengine.entities.UsableItem;
import com.benleskey.textengine.entities.UsableOnTarget;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.RelationshipDescriptor;
import com.benleskey.textengine.systems.ActorActionSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.ItemDescriptionSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.TagInteractionSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * ItemInteractionPlugin handles all item-related commands:
 * - Basic manipulation: take, drop, examine, inventory
 * - Item usage: use (solo or on target)
 * - Container operations: open, close, put
 * - Taking from containers: take X from Y
 */
public class ItemInteractionPlugin extends Plugin implements OnPluginInitialize {
	public static final String TAKE = "take";
	public static final String DROP = "drop";
	public static final String EXAMINE = "examine";
	public static final String USE = "use";
	public static final String INVENTORY = "inventory";
	public static final String OPEN = "open";
	public static final String CLOSE = "close";
	public static final String PUT = "put";
	
	public static final String M_ITEM = "item";
	public static final String M_ENTITY_ID = "entity_id";
	public static final String M_ITEM_NAME = "item_name";
	public static final String M_SUCCESS = "success";
	public static final String M_ERROR = "error";
	public static final String M_TARGET = "target";
	public static final String M_CONTAINER = "container";
	public static final String M_ITEMS = "items";
	public static final String M_WEIGHT = "weight";
	public static final String M_CARRY_WEIGHT = "carry_weight";
	public static final String M_CONTAINER_NAME = "container_name";
	
	public ItemInteractionPlugin(Game game) {
		super(game);
	}
	
	@Override
	public void onPluginInitialize() {
		// Take/get item (or take from container)
		// Accepts entity names or entity IDs with # prefix (e.g., "take #1234" or "take coin")
		game.registerCommand(new Command(TAKE, this::handleTake,
			new CommandVariant("take_from", "^(?:take|get)\\s+(.+?)\\s+from\\s+(.+?)\\s*$", this::parseTakeFrom),
			new CommandVariant("take_item", "^(?:take|get|pickup|grab)\\s+(.+?)\\s*$", this::parseTake)
		));
		
		// Drop item (accepts entity names or #IDs)
		game.registerCommand(new Command(DROP, this::handleDrop,
			new CommandVariant("drop_item", "^drop\\s+(.+?)\\s*$", this::parseDrop)
		));
		
		// Examine item (accepts entity names or #IDs)
		game.registerCommand(new Command(EXAMINE, this::handleExamine,
			new CommandVariant("examine_item", "^(?:examine|inspect|x)\\s+(.+?)\\s*$", this::parseExamine)
		));
		
		// Use item (or use item on target)
		// NOTE: Variants stored in HashMap (no guaranteed order), so regexes must be mutually exclusive
		// "use_on" matches: "use X on Y" or "use X with Y"
		// "use_item" matches: "use X" (but NOT if it contains "on" or "with" followed by more words)
		// Accepts entity names or #IDs for both item and target
		game.registerCommand(new Command(USE, this::handleUse,
			new CommandVariant("use_on", "^use\\s+(.+?)\\s+(?:on|with)\\s+(.+?)\\s*$", this::parseUseOn),
			new CommandVariant("use_item", "^use\\s+(?!.+?\\s+(?:on|with)\\s+)(.+?)\\s*$", this::parseUse)
		));
		
		// Inventory
		game.registerCommand(new Command(INVENTORY, this::handleInventory,
			new CommandVariant("inventory", "^(?:inventory|inv|i)\\s*$", args -> CommandInput.makeNone())
		));
		
		// Open container (accepts entity names or #IDs)
		game.registerCommand(new Command(OPEN, this::handleOpen,
			new CommandVariant("open_container", "^open\\s+(.+?)\\s*$", this::parseOpen)
		));
		
		// Close container (accepts entity names or #IDs)
		game.registerCommand(new Command(CLOSE, this::handleClose,
			new CommandVariant("close_container", "^close\\s+(.+?)\\s*$", this::parseClose)
		));
		
		// Put item in container (accepts entity names or #IDs)
		game.registerCommand(new Command(PUT, this::handlePut,
			new CommandVariant("put_in", "^put\\s+(.+?)\\s+(?:in|into)\\s+(.+?)\\s*$", this::parsePut)
		));
	}
	
	private CommandInput parseTake(Matcher matcher) {
		return CommandInput.makeNone().put(M_ITEM, matcher.group(1).trim());
	}
	
	private CommandInput parseTakeFrom(Matcher matcher) {
		return CommandInput.makeNone()
			.put(M_ITEM, matcher.group(1).trim())
			.put(M_CONTAINER, matcher.group(2).trim());
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
		ItemSystem is = game.getSystem(ItemSystem.class);
		
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
		
		// Check if taking from a container
		Entity sourceContainer = null;
		if (input.getO(M_CONTAINER).isPresent()) {
			// "take X from Y" syntax - get items from container
			String containerInput = input.get(M_CONTAINER);
			
			// Get containers at current location
			List<Entity> containersHere = rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.filter(e -> is.hasTag(e, is.TAG_CONTAINER, ws.getCurrentTime()))
				.collect(Collectors.toList());
			
			if (containersHere.isEmpty()) {
				client.sendOutput(CommandOutput.make(TAKE)
					.put(M_SUCCESS, false)
					.put(M_ERROR, "no_containers")
					.text(Markup.escape("There are no containers here.")));
				return;
			}
			
			DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
			java.util.function.Function<Entity, String> descExtractor = e -> {
				List<LookDescriptor> looks = ls.getLooksFromEntity(e, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			};
			
			DisambiguationSystem.ResolutionResult<Entity> containerResult = ds.resolveEntityWithAmbiguity(
				client,
				containerInput,
				containersHere,
				descExtractor
			);
			
			if (containerResult.isNotFound()) {
				client.sendOutput(CommandOutput.make(TAKE)
					.put(M_SUCCESS, false)
					.put(M_ERROR, "container_not_found")
					.text(Markup.concat(
						Markup.raw("You don't see "),
						Markup.em(containerInput),
						Markup.raw(" here.")
					)));
				return;
			}
			
			if (containerResult.isAmbiguous()) {
				handleAmbiguousMatch(client, TAKE, containerInput, containerResult.getAmbiguousMatches(), descExtractor);
				return;
			}
			
			sourceContainer = containerResult.getUniqueMatch();
			
			// Check if container is open
			Long openValue = is.getTagValue(sourceContainer, is.TAG_OPEN, ws.getCurrentTime());
			if (openValue == null || openValue == 0) {
				List<LookDescriptor> looks = ls.getLooksFromEntity(sourceContainer, ws.getCurrentTime());
				String containerName = !looks.isEmpty() ? looks.get(0).getDescription() : "the container";
				client.sendOutput(CommandOutput.make(TAKE)
					.put(M_SUCCESS, false)
					.put(M_ERROR, "container_closed")
					.text(Markup.concat(
						Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
						Markup.raw(" is closed.")
					)));
				return;
			}
		}
		
		// Get items from appropriate location (container or current location)
		Entity itemSource = sourceContainer != null ? sourceContainer : currentLocation;
		List<Entity> itemsHere = rs.getReceivingRelationships(itemSource, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		EntitySystem es = game.getSystem(EntitySystem.class);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		// Check if entity_id is provided (machine-readable input)
		Entity item = null;
		if (input.getO(M_ENTITY_ID).isPresent()) {
			String entityIdStr = input.get(M_ENTITY_ID);
			try {
				long entityId = Long.parseLong(entityIdStr);
				item = es.get(entityId);
				if (item == null || !itemsHere.contains(item)) {
					item = null; // Entity not found or not at this location
				}
			} catch (NumberFormatException e) {
				// Invalid entity ID
			}
		}
		
		// Fall back to keyword matching if no entity_id or entity not found
		String itemInput = input.get(M_ITEM);
		
		java.util.function.Function<Entity, String> descExtractor = e -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(e, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
		// Only do resolution if we don't already have an item from entity_id
		if (item == null) {
			DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
				client,
				itemInput,
				itemsHere,
				descExtractor
			);
			
			if (result.isNotFound()) {
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
			
			if (result.isAmbiguous()) {
				handleAmbiguousMatch(client, TAKE, itemInput, result.getAmbiguousMatches(), descExtractor);
				return;
			}
			
			item = result.getUniqueMatch();
		}
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Check if item is takeable
		if (!is.hasTag(item, is.TAG_TAKEABLE, ws.getCurrentTime())) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_takeable")
				.text(Markup.concat(
					Markup.raw("You can't take "),
					Markup.em(itemName),
					Markup.raw(".")
				)));
			return;
		}
		
		// Check weight constraints
		Long itemWeightGrams = is.getTagValue(item, is.TAG_WEIGHT, ws.getCurrentTime());
		Long carryWeightGrams = is.getTagValue(actor, is.TAG_CARRY_WEIGHT, ws.getCurrentTime());
		
		if (itemWeightGrams != null && carryWeightGrams != null) {
			com.benleskey.textengine.model.DWeight itemWeight = com.benleskey.textengine.model.DWeight.fromGrams(itemWeightGrams);
			com.benleskey.textengine.model.DWeight carryWeight = com.benleskey.textengine.model.DWeight.fromGrams(carryWeightGrams);
			
			if (itemWeight.isGreaterThan(carryWeight)) {
				client.sendOutput(CommandOutput.make(TAKE)
					.put(M_SUCCESS, false)
					.put(M_ERROR, "too_heavy")
					.put(M_WEIGHT, itemWeightGrams)
					.put(M_CARRY_WEIGHT, carryWeightGrams)
					.text(Markup.concat(
						Markup.em(itemName.substring(0, 1).toUpperCase() + itemName.substring(1)),
						Markup.raw(" is too heavy to carry. It weighs "),
						Markup.escape(itemWeight.toString()),
						Markup.raw(", but you can only carry up to "),
						Markup.escape(carryWeight.toString()),
						Markup.raw(".")
					)));
				return;
			}
		}
		
		// Calculate time for action - base 5s + 1s per kg
		ItemSystem itemSystem = game.getSystem(ItemSystem.class);
		Long weightGrams = itemSystem.getTagValue(item, itemSystem.TAG_WEIGHT, ws.getCurrentTime());
		long timeSeconds = 5;
		if (weightGrams != null) {
			long weightKg = weightGrams / 1000;
			timeSeconds = 5 + weightKg;
		}
		DTime actionTime = DTime.fromSeconds(timeSeconds);
		
		// Use ActorActionSystem to execute the take action
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		aas.queueAction((com.benleskey.textengine.entities.Actor) actor, aas.ACTION_ITEM_TAKE, item, actionTime);
		boolean success = aas.executeAction((com.benleskey.textengine.entities.Actor) actor, aas.ACTION_ITEM_TAKE, item, actionTime);
		
		if (!success) {
			client.sendOutput(CommandOutput.make(TAKE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "cannot_take")
				.text(Markup.concat(
					Markup.raw("You can't take "),
					Markup.em(itemName),
					Markup.raw(".")
				)));
			return;
		}
		
		client.sendOutput(CommandOutput.make(TAKE)
			.put(M_SUCCESS, true)
			.put(M_ENTITY_ID, String.valueOf(item.getId()))
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
		
		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
		DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
			client,
			itemInput,
			carriedItems,
			descExtractor
		);
		
		if (result.isNotFound()) {
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
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, DROP, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}
		
		Entity targetItem = result.getUniqueMatch();
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Use ActorActionSystem to execute the drop action
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		DTime dropTime = DTime.fromSeconds(5);
		
		aas.queueAction((com.benleskey.textengine.entities.Actor) actor, aas.ACTION_ITEM_DROP, targetItem, dropTime);
		boolean success = aas.executeAction((com.benleskey.textengine.entities.Actor) actor, aas.ACTION_ITEM_DROP, targetItem, dropTime);
		
		if (!success) {
			client.sendOutput(CommandOutput.make(DROP)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "cannot_drop")
				.text(Markup.concat(
					Markup.raw("You can't drop "),
					Markup.em(itemName),
					Markup.raw(".")
				)));
			return;
		}
		
		client.sendOutput(CommandOutput.make(DROP)
			.put(M_SUCCESS, true)
			.put(M_ENTITY_ID, String.valueOf(targetItem.getId()))
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
		ItemSystem is = game.getSystem(ItemSystem.class);
		ItemDescriptionSystem ids = game.getSystem(ItemDescriptionSystem.class);
		
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
		
		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
		DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
			client,
			itemInput,
			availableItems,
			descExtractor
		);
		
		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(EXAMINE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(itemInput),
					Markup.raw(".")
				)));
			return;
		}
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, EXAMINE, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}
		
		Entity targetItem = result.getUniqueMatch();
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "something";
		
		// Build examination output - all on one line
		java.util.List<Markup.Safe> examineMarkup = new java.util.ArrayList<>();
		examineMarkup.add(Markup.raw("You examine "));
		examineMarkup.add(Markup.raw(itemName));
		examineMarkup.add(Markup.raw(". "));
		
		// Add tag-based descriptions on same line with space separation
		List<String> tagDescriptions = ids.getDescriptions(targetItem, ws.getCurrentTime());
		for (int i = 0; i < tagDescriptions.size(); i++) {
			if (i > 0) {
				examineMarkup.add(Markup.raw(" ")); // Space between descriptions
			}
			examineMarkup.add(Markup.escape(tagDescriptions.get(i)));
		}
		
		// Add weight if present
		Long weightGrams = is.getTagValue(targetItem, is.TAG_WEIGHT, ws.getCurrentTime());
		if (weightGrams != null) {
			if (!tagDescriptions.isEmpty()) {
				examineMarkup.add(Markup.raw(" ")); // Space before weight
			}
			com.benleskey.textengine.model.DWeight weight = com.benleskey.textengine.model.DWeight.fromGrams(weightGrams);
			examineMarkup.add(Markup.escape("Weight: " + weight.toString()));
		}
		
		// Check if it's a container with contents
		List<Entity> contents = rs.getReceivingRelationships(targetItem, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Check if entity provides dynamic description
		if (targetItem instanceof com.benleskey.textengine.entities.DynamicDescription dynamicDesc) {
			String description = dynamicDesc.getDynamicDescription();
			if (description != null && !description.isEmpty()) {
				examineMarkup.add(Markup.raw("\n"));
				examineMarkup.add(Markup.escape(description));
			}
		}
		
		// If it's a container, show contents
		if (!contents.isEmpty()) {
			examineMarkup.add(Markup.raw("\nIt contains: "));
			
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
						examineMarkup.add(Markup.raw(", and "));
					} else {
						examineMarkup.add(Markup.raw(", "));
					}
				}
				examineMarkup.add(contentParts.get(i));
			}
			examineMarkup.add(Markup.raw("."));
		}
		
		// Build machine-readable contents list
		List<Map<String, Object>> itemsList = new java.util.ArrayList<>();
		for (Entity item : contents) {
			List<LookDescriptor> itemLooks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			String desc = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "something";
			
			Map<String, Object> itemData = new java.util.HashMap<>();
			itemData.put(M_ENTITY_ID, item.getKeyId());
			itemData.put(M_ITEM_NAME, desc);
			itemsList.add(itemData);
		}
		
		client.sendOutput(CommandOutput.make(EXAMINE)
			.put(M_SUCCESS, true)
			.put(M_ENTITY_ID, String.valueOf(targetItem.getId()))
			.put(M_ITEM, targetItem)
			.put(M_ITEMS, itemsList)
			.text(Markup.concat(examineMarkup.toArray(new Markup.Safe[0]))));
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
		
		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
		DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
			client,
			itemInput,
			carriedItems,
			descExtractor
		);
		
		if (result.isNotFound()) {
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
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, USE, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}
		
		Entity targetItem = result.getUniqueMatch();
		
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
		
		// Check if item implements UsableItem interface
		if (item instanceof UsableItem usableItem) {
			CommandOutput output = usableItem.useSolo(client, actor);
			if (output != null) {
				client.sendOutput(output);
				return;
			}
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
		
		java.util.function.Function<Entity, String> descExtractor = entity -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(entity, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
		DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
			client,
			targetInput,
			availableTargets,
			descExtractor
		);
		
		if (result.isNotFound()) {
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
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, USE, targetInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}
		
		Entity target = result.getUniqueMatch();
		
		// Get target description
		List<LookDescriptor> targetLooks = ls.getLooksFromEntity(target, ws.getCurrentTime());
		String targetName = !targetLooks.isEmpty() ? targetLooks.get(0).getDescription() : "the target";
		
		// GENERIC TAG-BASED INTERACTIONS
		
		// Check TagInteractionSystem for registered tag interactions
		TagInteractionSystem tis = game.getSystem(TagInteractionSystem.class);
		
		// Get actor name for broadcasting
		List<LookDescriptor> actorLooks = ls.getLooksFromEntity(actor, ws.getCurrentTime());
		String actorName = !actorLooks.isEmpty() ? actorLooks.get(0).getDescription() : "someone";
		
		Optional<CommandOutput> tagInteractionOutput = tis.executeInteractionWithBroadcast(
			actor, actorName, item, itemName, target, targetName, ws.getCurrentTime()
		);
		
		if (tagInteractionOutput.isPresent()) {
			client.sendOutput(tagInteractionOutput.get());
			return;
		}
		
		// Check if item implements UsableOnTarget interface (legacy/custom interactions)
		if (item instanceof UsableOnTarget usableOnTarget) {
			CommandOutput output = usableOnTarget.useOn(client, actor, target, targetName);
			if (output != null) {
				client.sendOutput(output);
				return;
			}
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
		
		// Build simple list without numeric IDs
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
		parts.add(Markup.raw("You are carrying: "));
		
		for (int i = 0; i < carriedItems.size(); i++) {
			if (i > 0) {
				if (i == carriedItems.size() - 1) {
					parts.add(Markup.raw(", and "));
				} else {
					parts.add(Markup.raw(", "));
				}
			}
			
			// Get item description
			List<LookDescriptor> looks = ls.getLooksFromEntity(carriedItems.get(i), ws.getCurrentTime());
			String description = !looks.isEmpty() ? looks.get(0).getDescription() : "something";
			parts.add(Markup.em(description));
		}
		parts.add(Markup.raw("."));
		
		client.sendOutput(CommandOutput.make(INVENTORY)
			.put(M_SUCCESS, true)
			.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}
	
	/**
	 * Helper method to handle ambiguous entity resolution.
	 * Creates a temporary numeric ID mapping and prompts the user to clarify.
	 * 
	 * @param client The client making the request
	 * @param commandId The command ID (e.g., TAKE, DROP, EXAMINE)
	 * @param userInput The original user input that was ambiguous
	 * @param matches The list of matching entities
	 * @param descriptionExtractor Function to extract description from entity
	 */
	private <T extends Entity> void handleAmbiguousMatch(
			Client client,
			String commandId,
			String userInput,
			List<T> matches,
			java.util.function.Function<T, String> descriptionExtractor) {
		
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		ds.sendDisambiguationPrompt(client, commandId, userInput, matches, descriptionExtractor);
	}
	
	private CommandInput parseOpen(Matcher matcher) {
		return CommandInput.makeNone().put(M_CONTAINER, matcher.group(1).trim());
	}
	
	private CommandInput parseClose(Matcher matcher) {
		return CommandInput.makeNone().put(M_CONTAINER, matcher.group(1).trim());
	}
	
	private CommandInput parsePut(Matcher matcher) {
		return CommandInput.makeNone()
			.put(M_ITEM, matcher.group(1).trim())
			.put(M_CONTAINER, matcher.group(2).trim());
	}
	
	private void handleOpen(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		// Get current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get all visible containers (in location or in inventory)
		List<Entity> allItems = new java.util.ArrayList<>();
		
		// Items at location
		allItems.addAll(rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		
		// Items in inventory
		allItems.addAll(rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		
		// Filter to only containers
		List<Entity> availableContainers = allItems.stream()
			.filter(item -> is.hasTag(item, is.TAG_CONTAINER, ws.getCurrentTime()))
			.collect(Collectors.toList());
		
		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "no_containers")
				.text(Markup.escape("There are no containers here.")));
			return;
		}
		
		String containerInput = input.get(M_CONTAINER);
		
		// Resolve which container
		var result = ds.resolveEntityWithAmbiguity(client, containerInput, availableContainers,
			container -> {
				var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			});
		
		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(containerInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, OPEN, containerInput, result.getAmbiguousMatches(),
				container -> {
					var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});
			return;
		}
		
		Entity container = result.getUniqueMatch();
		var containerLooks = ls.getLooksFromEntity(container, ws.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";
		
		// Check if already open
		Long openValue = is.getTagValue(container, is.TAG_OPEN, ws.getCurrentTime());
		if (openValue != null && openValue == 1) {
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "already_open")
				.put(M_CONTAINER, container.getKeyId())
				.put(M_CONTAINER_NAME, containerName)
				.text(Markup.concat(
					Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
					Markup.raw(" is already open.")
				)));
			return;
		}
		
		// Open the container
		is.addTag(container, is.TAG_OPEN, 1);
		
		// Get contents
		List<Entity> contents = rs.getReceivingRelationships(container, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (contents.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, true)
				.put(M_CONTAINER, container.getKeyId())
				.put(M_CONTAINER_NAME, containerName)
				.put(M_ITEMS, new java.util.ArrayList<String>())
				.text(Markup.concat(
					Markup.raw("You open "),
					Markup.em(containerName),
					Markup.raw(". It is empty.")
				)));
		} else {
			// Build machine-readable contents list
			List<Map<String, Object>> itemsList = new java.util.ArrayList<>();
			
			// Format contents list for human-readable text
			List<Markup.Safe> contentParts = new java.util.ArrayList<>();
			contentParts.add(Markup.raw("You open "));
			contentParts.add(Markup.em(containerName));
			contentParts.add(Markup.raw(". Inside you see "));
			
			for (int i = 0; i < contents.size(); i++) {
				Entity item = contents.get(i);
				var looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				String desc = !looks.isEmpty() ? looks.get(0).getDescription() : "something";
				
				// Add to machine-readable list
				Map<String, Object> itemData = new java.util.HashMap<>();
				itemData.put(M_ENTITY_ID, item.getKeyId());
				itemData.put(M_ITEM_NAME, desc);
				itemsList.add(itemData);
				
				// Add to human-readable text
				if (i > 0) {
					if (i == contents.size() - 1) {
						contentParts.add(Markup.raw(", and "));
					} else {
						contentParts.add(Markup.raw(", "));
					}
				}
				contentParts.add(Markup.em(desc));
			}
			contentParts.add(Markup.raw("."));
			
			client.sendOutput(CommandOutput.make(OPEN)
				.put(M_SUCCESS, true)
				.put(M_CONTAINER, container.getKeyId())
				.put(M_CONTAINER_NAME, containerName)
				.put(M_ITEMS, itemsList)
				.text(Markup.concat(contentParts.toArray(new Markup.Safe[0]))));
		}
	}
	
	private void handleClose(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		// Get current location
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(CLOSE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = containers.get(0).getProvider();
		
		// Get all visible containers
		List<Entity> allItems = new java.util.ArrayList<>();
		allItems.addAll(rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		allItems.addAll(rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		
		List<Entity> availableContainers = allItems.stream()
			.filter(item -> is.hasTag(item, is.TAG_CONTAINER, ws.getCurrentTime()))
			.collect(Collectors.toList());
		
		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(CLOSE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "no_containers")
				.text(Markup.escape("There are no containers here.")));
			return;
		}
		
		String containerInput = input.get(M_CONTAINER);
		
		var result = ds.resolveEntityWithAmbiguity(client, containerInput, availableContainers,
			container -> {
				var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			});
		
		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(CLOSE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(containerInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, CLOSE, containerInput, result.getAmbiguousMatches(),
				container -> {
					var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});
			return;
		}
		
		Entity container = result.getUniqueMatch();
		var containerLooks = ls.getLooksFromEntity(container, ws.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";
		
		// Check if already closed
		Long openValue = is.getTagValue(container, is.TAG_OPEN, ws.getCurrentTime());
		if (openValue == null || openValue == 0) {
			client.sendOutput(CommandOutput.make(CLOSE)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "already_closed")
				.put(M_CONTAINER, container.getKeyId())
				.put(M_CONTAINER_NAME, containerName)
				.text(Markup.concat(
					Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
					Markup.raw(" is already closed.")
				)));
			return;
		}
		
		// Close the container
		is.updateTagValue(container, is.TAG_OPEN, 0, ws.getCurrentTime());
		
		client.sendOutput(CommandOutput.make(CLOSE)
			.put(M_SUCCESS, true)
			.put(M_CONTAINER, container.getKeyId())
			.put(M_CONTAINER_NAME, containerName)
			.text(Markup.concat(
				Markup.raw("You close "),
				Markup.em(containerName),
				Markup.raw(".")
			)));
	}
	
	private void handlePut(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}
		
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);
		ItemSystem is = game.getSystem(ItemSystem.class);
		LookSystem ls = game.getSystem(LookSystem.class);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		com.benleskey.textengine.systems.EventSystem evs = game.getSystem(com.benleskey.textengine.systems.EventSystem.class);
		
		// Get current location
		var locationContainers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (locationContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere.")));
			return;
		}
		
		Entity currentLocation = locationContainers.get(0).getProvider();
		
		// Get items in inventory
		List<Entity> inventory = rs.getReceivingRelationships(actor, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		if (inventory.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "nothing_to_put")
				.text(Markup.escape("You aren't carrying anything.")));
			return;
		}
		
		String itemInput = input.get(M_ITEM);
		
		// Resolve which item to put
		var itemResult = ds.resolveEntityWithAmbiguity(client, itemInput, inventory,
			item -> {
				var looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			});
		
		if (itemResult.isNotFound()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "not_carrying")
				.text(Markup.concat(
					Markup.raw("You aren't carrying "),
					Markup.em(itemInput),
					Markup.raw(".")
				)));
			return;
		}
		
		if (itemResult.isAmbiguous()) {
			handleAmbiguousMatch(client, PUT, itemInput, itemResult.getAmbiguousMatches(),
				item -> {
					var looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});
			return;
		}
		
		Entity item = itemResult.getUniqueMatch();
		var itemLooks = ls.getLooksFromEntity(item, ws.getCurrentTime());
		String itemName = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "it";
		
		// Get all visible containers
		List<Entity> allItems = new java.util.ArrayList<>();
		allItems.addAll(rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList()));
		allItems.addAll(inventory);
		
		List<Entity> availableContainers = allItems.stream()
			.filter(container -> is.hasTag(container, is.TAG_CONTAINER, ws.getCurrentTime()))
			.collect(Collectors.toList());
		
		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "no_containers")
				.text(Markup.escape("There are no containers here.")));
			return;
		}
		
		String containerInput = input.get(M_CONTAINER);
		
		// Resolve which container
		var containerResult = ds.resolveEntityWithAmbiguity(client, containerInput, availableContainers,
			container -> {
				var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
				return !looks.isEmpty() ? looks.get(0).getDescription() : null;
			});
		
		if (containerResult.isNotFound()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "container_not_found")
				.text(Markup.concat(
					Markup.raw("You don't see "),
					Markup.em(containerInput),
					Markup.raw(" here.")
				)));
			return;
		}
		
		if (containerResult.isAmbiguous()) {
			handleAmbiguousMatch(client, PUT, containerInput, containerResult.getAmbiguousMatches(),
				container -> {
					var looks = ls.getLooksFromEntity(container, ws.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});
			return;
		}
		
		Entity container = containerResult.getUniqueMatch();
		var containerLooks = ls.getLooksFromEntity(container, ws.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";
		
		// Check if container is open
		Long openValue = is.getTagValue(container, is.TAG_OPEN, ws.getCurrentTime());
		if (openValue == null || openValue == 0) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "container_closed")
				.put(M_CONTAINER, container.getKeyId())
				.put(M_CONTAINER_NAME, containerName)
				.text(Markup.concat(
					Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
					Markup.raw(" is closed.")
				)));
			return;
		}
		
		// Can't put container inside itself
		if (item.getId() == container.getId()) {
			client.sendOutput(CommandOutput.make(PUT)
				.put(M_SUCCESS, false)
				.put(M_ERROR, "container_self")
				.text(Markup.escape("You can't put something inside itself.")));
			return;
		}
		
		// Move item from inventory to container
		// Cancel old containment relationship
		var oldContainment = rs.getProvidingRelationships(item, rs.rvContains, ws.getCurrentTime());
		if (!oldContainment.isEmpty()) {
			evs.cancelEvent(oldContainment.get(0).getRelationship());
		}
		
		// Add new containment relationship
		rs.add(container, item, rs.rvContains);
		
		client.sendOutput(CommandOutput.make(PUT)
			.put(M_SUCCESS, true)
			.put(M_ITEM, item.getKeyId())
			.put(M_ITEM_NAME, itemName)
			.put(M_CONTAINER, container.getKeyId())
			.put(M_CONTAINER_NAME, containerName)
			.text(Markup.concat(
				Markup.raw("You put "),
				Markup.em(itemName),
				Markup.raw(" in "),
				Markup.em(containerName),
				Markup.raw(".")
			)));
	}
}
