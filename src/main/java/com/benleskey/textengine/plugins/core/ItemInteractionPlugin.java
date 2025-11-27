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
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.RelationshipDescriptor;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.ItemDescriptionSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.TagInteractionSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Optional;
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
	public static final String M_WEIGHT = "weight";
	public static final String M_CARRY_WEIGHT = "carry_weight";
	
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
		
		// Get items at current location (not carried by actor)
		List<Entity> itemsHere = rs.getReceivingRelationships(currentLocation, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(RelationshipDescriptor::getReceiver)
			.filter(e -> e instanceof Item)
			.collect(Collectors.toList());
		
		// Resolve which item the player wants
		String itemInput = input.get(M_ITEM);
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = ls.getLooksFromEntity(item, ws.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};
		
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
		
		Entity targetItem = result.getUniqueMatch();
		
		// Get item description
		List<LookDescriptor> looks = ls.getLooksFromEntity(targetItem, ws.getCurrentTime());
		String itemName = !looks.isEmpty() ? looks.get(0).getDescription() : "the item";
		
		// Check if item is takeable
		if (!is.hasTag(targetItem, is.TAG_TAKEABLE, ws.getCurrentTime())) {
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
		Long itemWeightGrams = is.getTagValue(targetItem, is.TAG_WEIGHT, ws.getCurrentTime());
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
		
		client.sendOutput(CommandOutput.make(EXAMINE)
			.put(M_SUCCESS, true)
			.put(M_ITEM, targetItem)
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
		Optional<CommandOutput> tagInteractionOutput = tis.executeInteraction(
			client, actor, item, itemName, target, targetName, ws.getCurrentTime()
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
		
		// Build disambiguated list with numeric IDs
		DisambiguationSystem.DisambiguatedList list = ds.buildDisambiguatedList(matches, descriptionExtractor);
		
		// Update client's numeric ID map so they can use numbers
		client.setNumericIdMap(list.getNumericIdMap());
		
		// Format output
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
		parts.add(Markup.raw("Which "));
		parts.add(Markup.em(userInput));
		parts.add(Markup.raw(" did you mean? "));
		
		List<Markup.Safe> itemParts = list.getMarkupParts();
		for (int i = 0; i < itemParts.size(); i++) {
			if (i > 0) {
				if (i == itemParts.size() - 1) {
					parts.add(Markup.raw(", or "));
				} else {
					parts.add(Markup.raw(", "));
				}
			}
			parts.add(itemParts.get(i));
		}
		parts.add(Markup.raw("?"));
		
		client.sendOutput(CommandOutput.make(commandId)
			.put(M_SUCCESS, false)
			.put(M_ERROR, "ambiguous")
			.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}
}
