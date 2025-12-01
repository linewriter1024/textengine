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
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.model.LookDescriptor;
import com.benleskey.textengine.model.RelationshipDescriptor;
import com.benleskey.textengine.plugins.procgen1.systems.ProceduralWorldPlugin;
import com.benleskey.textengine.systems.ActorActionSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.EntitySystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.TagInteractionSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	// Note: EntitySystem.M_ENTITY_ID defined in EntitySystem

	// Error codes
	public static final String ERR_PLAYER_NOWHERE = "player_nowhere";
	public static final String ERR_NO_CONTAINERS = "no_containers";
	public static final String ERR_CONTAINER_NOT_FOUND = "container_not_found";
	public static final String ERR_CONTAINER_CLOSED = "container_closed";
	public static final String ERR_ITEM_NOT_FOUND = "item_not_found";
	public static final String ERR_NOT_TAKEABLE = "not_takeable";
	public static final String ERR_TOO_HEAVY = "too_heavy";
	public static final String ERR_CANNOT_TAKE = "cannot_take";
	public static final String ERR_EMPTY_INVENTORY = "empty_inventory";
	public static final String ERR_NOT_CARRYING = "not_carrying";
	public static final String ERR_CANNOT_DROP = "cannot_drop";
	public static final String ERR_NO_USE = "no_use";
	public static final String ERR_TARGET_NOT_FOUND = "target_not_found";
	public static final String ERR_NO_INTERACTION = "no_interaction";
	public static final String ERR_ALREADY_OPEN = "already_open";
	public static final String ERR_ALREADY_CLOSED = "already_closed";
	public static final String ERR_NOTHING_TO_PUT = "nothing_to_put";
	public static final String ERR_CONTAINER_SELF = "container_self";

	// System fields
	private RelationshipSystem relationshipSystem;
	private WorldSystem worldSystem;
	private LookSystem lookSystem;
	private ItemSystem itemSystem;
	private EntitySystem entitySystem;
	private DisambiguationSystem disambiguationSystem;
	private EntityDescriptionSystem entityDescriptionSystem;
	private ActorActionSystem actorActionSystem;
	private TagInteractionSystem tagInteractionSystem;
	private com.benleskey.textengine.systems.EventSystem eventSystem;

	public ItemInteractionPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(
				game.getPlugin(EntityPlugin.class),
				game.getPlugin(ProceduralWorldPlugin.class));
	}

	@Override
	public void onPluginInitialize() {
		game.registerSystem(new TagInteractionSystem(game));

		// Initialize systems
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		lookSystem = game.getSystem(LookSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);
		entitySystem = game.getSystem(EntitySystem.class);
		disambiguationSystem = game.getSystem(DisambiguationSystem.class);
		entityDescriptionSystem = game.getSystem(EntityDescriptionSystem.class);
		actorActionSystem = game.getSystem(ActorActionSystem.class);
		tagInteractionSystem = game.getSystem(TagInteractionSystem.class);
		eventSystem = game.getSystem(com.benleskey.textengine.systems.EventSystem.class);

		// Take/get item (or take from container)
		// Accepts entity names or entity IDs with # prefix (e.g., "take #1234" or "take
		// coin")
		game.registerCommand(new Command(TAKE, this::handleTake,
				new CommandVariant("take_from", "^(?:take|get)\\s+(.+?)\\s+from\\s+(.+?)\\s*$", this::parseTakeFrom),
				new CommandVariant("take_item", "^(?:take|get|pickup|grab)\\s+(.+?)\\s*$", this::parseTake)));

		// Drop item (accepts entity names or #IDs)
		game.registerCommand(new Command(DROP, this::handleDrop,
				new CommandVariant("drop_item", "^drop\\s+(.+?)\\s*$", this::parseDrop)));

		// Examine item (accepts entity names or #IDs)
		game.registerCommand(new Command(EXAMINE, this::handleExamine,
				new CommandVariant("examine_item", "^(?:examine|inspect|x)\\s+(.+?)\\s*$", this::parseExamine)));

		// Use item (or use item on target)
		// NOTE: Variants stored in HashMap (no guaranteed order), so regexes must be
		// mutually exclusive
		// "use_on" matches: "use X on Y" or "use X with Y"
		// "use_item" matches: "use X" (but NOT if it contains "on" or "with" followed
		// by more words)
		// Accepts entity names or #IDs for both item and target
		game.registerCommand(new Command(USE, this::handleUse,
				new CommandVariant("use_on", "^use\\s+(.+?)\\s+(?:on|with)\\s+(.+?)\\s*$", this::parseUseOn),
				new CommandVariant("use_item", "^use\\s+(?!.+?\\s+(?:on|with)\\s+)(.+?)\\s*$", this::parseUse)));

		// Inventory
		game.registerCommand(new Command(INVENTORY, this::handleInventory,
				new CommandVariant("inventory", "^(?:inventory|inv|i)\\s*$", args -> CommandInput.makeNone())));

		// Open container (accepts entity names or #IDs)
		game.registerCommand(new Command(OPEN, this::handleOpen,
				new CommandVariant("open_container", "^open\\s+(.+?)\\s*$", this::parseOpen)));

		// Close container (accepts entity names or #IDs)
		game.registerCommand(new Command(CLOSE, this::handleClose,
				new CommandVariant("close_container", "^close\\s+(.+?)\\s*$", this::parseClose)));

		// Put item in container (accepts entity names or #IDs)
		game.registerCommand(new Command(PUT, this::handlePut,
				new CommandVariant("put_in", "^put\\s+(.+?)\\s+(?:in|into)\\s+(.+?)\\s*$", this::parsePut)));
	}

	private CommandInput parseTake(Matcher matcher) {
		return CommandInput.makeNone().put(ItemSystem.M_ITEM, matcher.group(1).trim());
	}

	private CommandInput parseTakeFrom(Matcher matcher) {
		return CommandInput.makeNone()
				.put(ItemSystem.M_ITEM, matcher.group(1).trim())
				.put(RelationshipSystem.M_CONTAINER, matcher.group(2).trim());
	}

	private CommandInput parseDrop(Matcher matcher) {
		return CommandInput.makeNone().put(ItemSystem.M_ITEM, matcher.group(1).trim());
	}

	private CommandInput parseExamine(Matcher matcher) {
		return CommandInput.makeNone().put(ItemSystem.M_ITEM, matcher.group(1).trim());
	}

	private CommandInput parseUse(Matcher matcher) {
		return CommandInput.makeNone().put(ItemSystem.M_ITEM, matcher.group(1).trim());
	}

	private CommandInput parseUseOn(Matcher matcher) {
		return CommandInput.makeNone()
				.put(ItemSystem.M_ITEM, matcher.group(1).trim())
				.put(RelationshipSystem.M_TARGET, matcher.group(2).trim());
	}

	private void handleTake(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Find current location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(TAKE)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Check if taking from a container
		Entity sourceContainer = null;
		if (input.getO(RelationshipSystem.M_CONTAINER).isPresent()) {
			// "take X from Y" syntax - get items from container
			String containerInput = input.get(RelationshipSystem.M_CONTAINER);

			// Get containers at current location
			List<Entity> containersHere = relationshipSystem
					.getReceivingRelationships(currentLocation, relationshipSystem.rvContains,
							worldSystem.getCurrentTime())
					.stream()
					.map(RelationshipDescriptor::getReceiver)
					.filter(e -> e instanceof Item)
					.filter(e -> itemSystem.hasTag(e, itemSystem.TAG_CONTAINER, worldSystem.getCurrentTime()))
					.collect(Collectors.toList());

			if (containersHere.isEmpty()) {
				client.sendOutput(CommandOutput.make(TAKE)
						.error(ERR_NO_CONTAINERS)
						.text(Markup.escape("There are no containers here.")));
				return;
			}

			java.util.function.Function<Entity, String> descExtractor = e -> entityDescriptionSystem
					.getSimpleDescription(e, worldSystem.getCurrentTime());

			DisambiguationSystem.ResolutionResult<Entity> containerResult = disambiguationSystem
					.resolveEntityWithAmbiguity(
							client,
							containerInput,
							containersHere,
							descExtractor);

			if (containerResult.isNotFound()) {
				client.sendOutput(CommandOutput.make(TAKE)
						.error(ERR_CONTAINER_NOT_FOUND)
						.text(Markup.concat(
								Markup.raw("You don't see "),
								Markup.em(containerInput),
								Markup.raw(" here."))));
				return;
			}

			if (containerResult.isAmbiguous()) {
				handleAmbiguousMatch(client, TAKE, containerInput, containerResult.getAmbiguousMatches(),
						descExtractor);
				return;
			}

			sourceContainer = containerResult.getUniqueMatch();

			// Check if container is open
			Long openValue = itemSystem.getTagValue(sourceContainer, itemSystem.TAG_OPEN, worldSystem.getCurrentTime());
			if (openValue == null || openValue == 0) {
				String containerName = entityDescriptionSystem.getSimpleDescription(sourceContainer,
						worldSystem.getCurrentTime(), "the container");
				client.sendOutput(CommandOutput.make(TAKE)
						.error(ERR_CONTAINER_CLOSED)
						.text(Markup.concat(
								Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
								Markup.raw(" is closed."))));
				return;
			}
		}

		// Get items from appropriate location (container or current location)
		Entity itemSource = sourceContainer != null ? sourceContainer : currentLocation;
		List<Entity> itemsHere = relationshipSystem
				.getReceivingRelationships(itemSource, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		// Check if entity_id is provided (machine-readable input)
		Entity item = null;
		if (input.getO(EntitySystem.M_ENTITY_ID).isPresent()) {
			String entityIdStr = input.get(EntitySystem.M_ENTITY_ID);
			try {
				long entityId = Long.parseLong(entityIdStr);
				item = entitySystem.get(entityId);
				if (item == null || !itemsHere.contains(item)) {
					item = null; // Entity not found or not at this location
				}
			} catch (NumberFormatException e) {
				// Invalid entity ID
			}
		}

		// Fall back to keyword matching if no entity_id or entity not found
		String itemInput = input.get(ItemSystem.M_ITEM);

		java.util.function.Function<Entity, String> descExtractor = e -> entityDescriptionSystem.getSimpleDescription(e,
				worldSystem.getCurrentTime());

		// Only do resolution if we don't already have an item from entity_id
		if (item == null) {
			DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
					client,
					itemInput,
					itemsHere,
					descExtractor);

			if (result.isNotFound()) {
				client.sendOutput(CommandOutput.make(TAKE)
						.error(ERR_ITEM_NOT_FOUND)
						.text(Markup.concat(
								Markup.raw("You don't see "),
								Markup.em(itemInput),
								Markup.raw(" here."))));
				return;
			}

			if (result.isAmbiguous()) {
				handleAmbiguousMatch(client, TAKE, itemInput, result.getAmbiguousMatches(), descExtractor);
				return;
			}

			item = result.getUniqueMatch();
		}

		// Calculate time for action - base 5s + 1s per kg
		Long weightGrams = itemSystem.getTagValue(item, itemSystem.TAG_WEIGHT, worldSystem.getCurrentTime());
		long timeSeconds = 5;
		if (weightGrams != null) {
			long weightKg = weightGrams / 1000;
			timeSeconds = 5 + weightKg;
		}
		DTime actionTime = DTime.fromSeconds(timeSeconds);

		// Queue the action (validation + execution happens inside)

		ActionValidation validation = actorActionSystem.queueAction((com.benleskey.textengine.entities.Actor) actor,
				actorActionSystem.ACTION_ITEM_TAKE, item, actionTime);

		if (!validation.isValid()) {
			client.sendOutput(validation.getErrorOutput());
			return;
		}

		// Success - action has already broadcast the result to player
	}

	private void handleDrop(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Find current location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		// Get items carried by actor
		List<Entity> carriedItems = relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(DROP)
					.error(ERR_EMPTY_INVENTORY)
					.text(Markup.escape("You aren't carrying anything.")));
			return;
		}

		// Resolve which item to drop
		String itemInput = input.get(ItemSystem.M_ITEM);

		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};

		DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
				client,
				itemInput,
				carriedItems,
				descExtractor);

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(DROP)
					.error(ERR_NOT_CARRYING)
					.text(Markup.concat(
							Markup.raw("You aren't carrying "),
							Markup.em(itemInput),
							Markup.raw("."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, DROP, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}

		Entity targetItem = result.getUniqueMatch();

		// Queue the action (validation + execution happens inside)

		DTime dropTime = DTime.fromSeconds(5);

		ActionValidation validation = actorActionSystem.queueAction((com.benleskey.textengine.entities.Actor) actor,
				actorActionSystem.ACTION_ITEM_DROP, targetItem, dropTime);

		if (!validation.isValid()) {
			client.sendOutput(validation.getErrorOutput());
			return;
		}

		// Success - action has already broadcast the result to player
	}

	private void handleExamine(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get items from both inventory and current location
		List<Entity> availableItems = new java.util.ArrayList<>();

		// Carried items
		availableItems.addAll(relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));

		// Items at location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (!containers.isEmpty()) {
			Entity currentLocation = containers.get(0).getProvider();
			availableItems.addAll(relationshipSystem
					.getReceivingRelationships(currentLocation, relationshipSystem.rvContains,
							worldSystem.getCurrentTime())
					.stream()
					.map(RelationshipDescriptor::getReceiver)
					.filter(e -> e instanceof Item)
					.collect(Collectors.toList()));
		}

		// Resolve which item to examine
		String itemInput = input.get(ItemSystem.M_ITEM);

		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};

		DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
				client,
				itemInput,
				availableItems,
				descExtractor);

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(EXAMINE)
					.error(ERR_ITEM_NOT_FOUND)
					.text(Markup.concat(
							Markup.raw("You don't see "),
							Markup.em(itemInput),
							Markup.raw("."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, EXAMINE, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}

		Entity targetItem = result.getUniqueMatch();

		// Get item description
		String itemName = entityDescriptionSystem.getSimpleDescription(targetItem, worldSystem.getCurrentTime(),
				"something");

		// Build examination output - all on one line
		java.util.List<Markup.Safe> examineMarkup = new java.util.ArrayList<>();
		examineMarkup.add(Markup.raw("You examine "));
		examineMarkup.add(Markup.raw(itemName));
		examineMarkup.add(Markup.raw(". "));

		// Add tag-based descriptions on same line with space separation
		List<String> tagDescriptions = entityDescriptionSystem.getTagDescriptions(targetItem,
				worldSystem.getCurrentTime());
		for (int i = 0; i < tagDescriptions.size(); i++) {
			if (i > 0) {
				examineMarkup.add(Markup.raw(" ")); // Space between descriptions
			}
			examineMarkup.add(Markup.escape(tagDescriptions.get(i)));
		}

		// Add weight if present
		Long weightGrams = itemSystem.getTagValue(targetItem, itemSystem.TAG_WEIGHT, worldSystem.getCurrentTime());
		if (weightGrams != null) {
			if (!tagDescriptions.isEmpty()) {
				examineMarkup.add(Markup.raw(" ")); // Space before weight
			}
			com.benleskey.textengine.model.DWeight weight = com.benleskey.textengine.model.DWeight
					.fromGrams(weightGrams);
			examineMarkup.add(Markup.escape("Weight: " + weight.toString()));
		}

		// Check if it's a container with contents
		List<Entity> contents = relationshipSystem
				.getReceivingRelationships(targetItem, relationshipSystem.rvContains, worldSystem.getCurrentTime())
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

			DisambiguationSystem.DisambiguatedList contentList = disambiguationSystem.buildDisambiguatedList(
					contents,
					item -> {
						List<LookDescriptor> itemLooks = lookSystem.getLooksFromEntity(item,
								worldSystem.getCurrentTime());
						return !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : null;
					});

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
			List<LookDescriptor> itemLooks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
			String desc = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "something";

			Map<String, Object> itemData = new java.util.HashMap<>();
			itemData.put(EntitySystem.M_ENTITY_ID, item.getKeyId());
			itemData.put(ItemSystem.M_ITEM_NAME, desc);
			itemsList.add(itemData);
		}

		client.sendOutput(CommandOutput.make(EXAMINE)
				.put(EntitySystem.M_ENTITY_ID, String.valueOf(targetItem.getId()))
				.put(ItemSystem.M_ITEM, targetItem)
				.put(ItemSystem.M_ITEMS, itemsList)
				.text(Markup.concat(examineMarkup.toArray(new Markup.Safe[0]))));
	}

	private void handleUse(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get carried items (can only use what you're carrying)
		List<Entity> carriedItems = relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(USE)
					.error(ERR_EMPTY_INVENTORY)
					.text(Markup.escape("You aren't carrying anything to use.")));
			return;
		}

		// Resolve which item to use
		String itemInput = input.get(ItemSystem.M_ITEM);

		java.util.function.Function<Entity, String> descExtractor = item -> {
			List<LookDescriptor> looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};

		DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
				client,
				itemInput,
				carriedItems,
				descExtractor);

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(USE)
					.error(ERR_NOT_CARRYING)
					.text(Markup.concat(
							Markup.raw("You aren't carrying "),
							Markup.em(itemInput),
							Markup.raw("."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, USE, itemInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}

		Entity targetItem = result.getUniqueMatch();

		// Check if using on a target
		java.util.Optional<Object> targetOpt = input.getO(RelationshipSystem.M_TARGET);

		if (targetOpt.isPresent()) {
			// Using item on target - delegate to ItemActionSystem
			handleUseOn(client, actor, targetItem, targetOpt.get().toString());
		} else {
			// Using item by itself
			handleUseSolo(client, actor, targetItem);
		}
	}

	private void handleUseSolo(Client client, Entity actor, Entity item) {

		List<LookDescriptor> looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
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
				.error(ERR_NO_USE)
				.text(Markup.concat(
						Markup.raw("You're not sure how to use "),
						Markup.em(itemName),
						Markup.raw(" by itself."))));
	}

	private void handleUseOn(Client client, Entity actor, Entity item, String targetInput) {
		// Find current location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(USE)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Get available targets (items at location or nearby entities)
		List<Entity> availableTargets = new java.util.ArrayList<>();

		// Items at location
		availableTargets.addAll(relationshipSystem
				.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.collect(Collectors.toList()));

		// Resolve target

		java.util.function.Function<Entity, String> descExtractor = entity -> {
			List<LookDescriptor> looks = lookSystem.getLooksFromEntity(entity, worldSystem.getCurrentTime());
			return !looks.isEmpty() ? looks.get(0).getDescription() : null;
		};

		DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
				client,
				targetInput,
				availableTargets,
				descExtractor);

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(USE)
					.error(ERR_TARGET_NOT_FOUND)
					.text(Markup.concat(
							Markup.raw("You don't see "),
							Markup.em(targetInput),
							Markup.raw(" here."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, USE, targetInput, result.getAmbiguousMatches(), descExtractor);
			return;
		}

		Entity target = result.getUniqueMatch();

		// GENERIC TAG-BASED INTERACTIONS

		// Check TagInteractionSystem for registered tag interactions

		boolean tagInteractionOutput = tagInteractionSystem.executeInteraction(
				actor, item, target, worldSystem.getCurrentTime());

		if (tagInteractionOutput) {
			return;
		}

		// Default: no interaction defined
		client.sendOutput(CommandOutput.make(USE)
				.error(ERR_NO_INTERACTION)
				.text(Markup.concat(
						Markup.raw("You can't use "),
						Markup.em(entityDescriptionSystem.getDescription(item, worldSystem.getCurrentTime())),
						Markup.raw(" on "),
						Markup.em(entityDescriptionSystem.getDescription(target, worldSystem.getCurrentTime())),
						Markup.raw("."))));
	}

	private void handleInventory(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get carried items
		List<Entity> carriedItems = relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		if (carriedItems.isEmpty()) {
			client.sendOutput(CommandOutput.make(INVENTORY)
					.put(ItemSystem.M_ITEMS, new java.util.ArrayList<String>())
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

			String description = entityDescriptionSystem.getSimpleDescription(carriedItems.get(i),
					worldSystem.getCurrentTime(), "something");
			parts.add(Markup.em(description));
		}
		parts.add(Markup.raw("."));

		client.sendOutput(CommandOutput.make(INVENTORY)
				.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
	}

	/**
	 * Helper method to handle ambiguous entity resolution.
	 * Creates a temporary numeric ID mapping and prompts the user to clarify.
	 * 
	 * @param client               The client making the request
	 * @param commandId            The command ID (e.g., TAKE, DROP, EXAMINE)
	 * @param userInput            The original user input that was ambiguous
	 * @param matches              The list of matching entities
	 * @param descriptionExtractor Function to extract description from entity
	 */
	private <T extends Entity> void handleAmbiguousMatch(
			Client client,
			String commandId,
			String userInput,
			List<T> matches,
			java.util.function.Function<T, String> descriptionExtractor) {

		disambiguationSystem.sendDisambiguationPrompt(client, commandId, userInput, matches, descriptionExtractor);
	}

	private CommandInput parseOpen(Matcher matcher) {
		return CommandInput.makeNone().put(RelationshipSystem.M_CONTAINER, matcher.group(1).trim());
	}

	private CommandInput parseClose(Matcher matcher) {
		return CommandInput.makeNone().put(RelationshipSystem.M_CONTAINER, matcher.group(1).trim());
	}

	private CommandInput parsePut(Matcher matcher) {
		return CommandInput.makeNone()
				.put(ItemSystem.M_ITEM, matcher.group(1).trim())
				.put(RelationshipSystem.M_CONTAINER, matcher.group(2).trim());
	}

	private void handleOpen(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get current location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Get all visible containers (in location or in inventory)
		List<Entity> allItems = new java.util.ArrayList<>();

		// Items at location
		allItems.addAll(relationshipSystem
				.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));

		// Items in inventory
		allItems.addAll(relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));

		// Filter to only containers
		List<Entity> availableContainers = allItems.stream()
				.filter(item -> itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, worldSystem.getCurrentTime()))
				.collect(Collectors.toList());

		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
					.error(ERR_NO_CONTAINERS)
					.text(Markup.escape("There are no containers here.")));
			return;
		}

		String containerInput = input.get(RelationshipSystem.M_CONTAINER);

		// Resolve which container
		var result = disambiguationSystem.resolveEntityWithAmbiguity(client, containerInput, availableContainers,
				container -> {
					var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(OPEN)
					.error(ERR_ITEM_NOT_FOUND)
					.text(Markup.concat(
							Markup.raw("You don't see "),
							Markup.em(containerInput),
							Markup.raw(" here."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, OPEN, containerInput, result.getAmbiguousMatches(),
					container -> {
						var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
						return !looks.isEmpty() ? looks.get(0).getDescription() : null;
					});
			return;
		}

		Entity container = result.getUniqueMatch();
		var containerLooks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";

		// Check if already open
		Long openValue = itemSystem.getTagValue(container, itemSystem.TAG_OPEN, worldSystem.getCurrentTime());
		if (openValue != null && openValue == 1) {
			client.sendOutput(CommandOutput.make(OPEN)
					.error(ERR_ALREADY_OPEN)
					.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
					.text(Markup.concat(
							Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
							Markup.raw(" is already open."))));
			return;
		}

		// Open the container
		itemSystem.addTag(container, itemSystem.TAG_OPEN, 1);

		// Get contents
		List<Entity> contents = relationshipSystem
				.getReceivingRelationships(container, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		if (contents.isEmpty()) {
			client.sendOutput(CommandOutput.make(OPEN)
					.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
					.put(ItemSystem.M_ITEMS, new java.util.ArrayList<String>())
					.text(Markup.concat(
							Markup.raw("You open "),
							Markup.em(containerName),
							Markup.raw(". It is empty."))));
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
				var looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
				String desc = !looks.isEmpty() ? looks.get(0).getDescription() : "something";

				// Add to machine-readable list
				Map<String, Object> itemData = new java.util.HashMap<>();
				itemData.put(EntitySystem.M_ENTITY_ID, item.getKeyId());
				itemData.put(ItemSystem.M_ITEM_NAME, desc);
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
					.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
					.put(ItemSystem.M_ITEMS, itemsList)
					.text(Markup.concat(contentParts.toArray(new Markup.Safe[0]))));
		}
	}

	private void handleClose(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get current location
		var containers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(CLOSE)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		Entity currentLocation = containers.get(0).getProvider();

		// Get all visible containers
		List<Entity> allItems = new java.util.ArrayList<>();
		allItems.addAll(relationshipSystem
				.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));
		allItems.addAll(relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));

		List<Entity> availableContainers = allItems.stream()
				.filter(item -> itemSystem.hasTag(item, itemSystem.TAG_CONTAINER, worldSystem.getCurrentTime()))
				.collect(Collectors.toList());

		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(CLOSE)
					.error(ERR_NO_CONTAINERS)
					.text(Markup.escape("There are no containers here.")));
			return;
		}

		String containerInput = input.get(RelationshipSystem.M_CONTAINER);

		var result = disambiguationSystem.resolveEntityWithAmbiguity(client, containerInput, availableContainers,
				container -> {
					var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});

		if (result.isNotFound()) {
			client.sendOutput(CommandOutput.make(CLOSE)
					.error(ERR_ITEM_NOT_FOUND)
					.text(Markup.concat(
							Markup.raw("You don't see "),
							Markup.em(containerInput),
							Markup.raw(" here."))));
			return;
		}

		if (result.isAmbiguous()) {
			handleAmbiguousMatch(client, CLOSE, containerInput, result.getAmbiguousMatches(),
					container -> {
						var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
						return !looks.isEmpty() ? looks.get(0).getDescription() : null;
					});
			return;
		}

		Entity container = result.getUniqueMatch();
		var containerLooks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";

		// Check if already closed
		Long openValue = itemSystem.getTagValue(container, itemSystem.TAG_OPEN, worldSystem.getCurrentTime());
		if (openValue == null || openValue == 0) {
			client.sendOutput(CommandOutput.make(CLOSE)
					.error(ERR_ALREADY_CLOSED)
					.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
					.text(Markup.concat(
							Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
							Markup.raw(" is already closed."))));
			return;
		}

		// Close the container
		itemSystem.updateTagValue(container, itemSystem.TAG_OPEN, 0, worldSystem.getCurrentTime());

		client.sendOutput(CommandOutput.make(CLOSE)
				.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
				.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
				.text(Markup.concat(
						Markup.raw("You close "),
						Markup.em(containerName),
						Markup.raw("."))));
	}

	private void handlePut(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get current location
		var locationContainers = relationshipSystem.getProvidingRelationships(actor, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (locationContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_PLAYER_NOWHERE)
					.text(Markup.escape("You are nowhere.")));
			return;
		}

		Entity currentLocation = locationContainers.get(0).getProvider();

		// Get items in inventory
		List<Entity> inventory = relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		if (inventory.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_NOTHING_TO_PUT)
					.text(Markup.escape("You aren't carrying anything.")));
			return;
		}

		String itemInput = input.get(ItemSystem.M_ITEM);

		// Resolve which item to put
		var itemResult = disambiguationSystem.resolveEntityWithAmbiguity(client, itemInput, inventory,
				item -> {
					var looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});

		if (itemResult.isNotFound()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_NOT_CARRYING)
					.text(Markup.concat(
							Markup.raw("You aren't carrying "),
							Markup.em(itemInput),
							Markup.raw("."))));
			return;
		}

		if (itemResult.isAmbiguous()) {
			handleAmbiguousMatch(client, PUT, itemInput, itemResult.getAmbiguousMatches(),
					item -> {
						var looks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
						return !looks.isEmpty() ? looks.get(0).getDescription() : null;
					});
			return;
		}

		Entity item = itemResult.getUniqueMatch();
		var itemLooks = lookSystem.getLooksFromEntity(item, worldSystem.getCurrentTime());
		String itemName = !itemLooks.isEmpty() ? itemLooks.get(0).getDescription() : "it";

		// Get all visible containers
		List<Entity> allItems = new java.util.ArrayList<>();
		allItems.addAll(relationshipSystem
				.getReceivingRelationships(currentLocation, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList()));
		allItems.addAll(inventory);

		List<Entity> availableContainers = allItems.stream()
				.filter(container -> itemSystem.hasTag(container, itemSystem.TAG_CONTAINER,
						worldSystem.getCurrentTime()))
				.collect(Collectors.toList());

		if (availableContainers.isEmpty()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_NO_CONTAINERS)
					.text(Markup.escape("There are no containers here.")));
			return;
		}

		String containerInput = input.get(RelationshipSystem.M_CONTAINER);

		// Resolve which container
		var containerResult = disambiguationSystem.resolveEntityWithAmbiguity(client, containerInput,
				availableContainers,
				container -> {
					var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				});

		if (containerResult.isNotFound()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_CONTAINER_NOT_FOUND)
					.text(Markup.concat(
							Markup.raw("You don't see "),
							Markup.em(containerInput),
							Markup.raw(" here."))));
			return;
		}

		if (containerResult.isAmbiguous()) {
			handleAmbiguousMatch(client, PUT, containerInput, containerResult.getAmbiguousMatches(),
					container -> {
						var looks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
						return !looks.isEmpty() ? looks.get(0).getDescription() : null;
					});
			return;
		}

		Entity container = containerResult.getUniqueMatch();
		var containerLooks = lookSystem.getLooksFromEntity(container, worldSystem.getCurrentTime());
		String containerName = !containerLooks.isEmpty() ? containerLooks.get(0).getDescription() : "the container";

		// Check if container is open
		Long openValue = itemSystem.getTagValue(container, itemSystem.TAG_OPEN, worldSystem.getCurrentTime());
		if (openValue == null || openValue == 0) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_CONTAINER_CLOSED)
					.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
					.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
					.text(Markup.concat(
							Markup.em(containerName.substring(0, 1).toUpperCase() + containerName.substring(1)),
							Markup.raw(" is closed."))));
			return;
		}

		// Can't put container inside itself
		if (item.getId() == container.getId()) {
			client.sendOutput(CommandOutput.make(PUT)
					.error(ERR_CONTAINER_SELF)
					.text(Markup.escape("You can't put something inside itself.")));
			return;
		}

		// Move item from inventory to container
		// Cancel old containment relationship event
		var oldContainment = relationshipSystem.getProvidingRelationships(item, relationshipSystem.rvContains,
				worldSystem.getCurrentTime());
		if (!oldContainment.isEmpty()) {
			eventSystem.cancelEventsByTypeAndReference(relationshipSystem.etEntityRelationship,
					oldContainment.get(0).getRelationship(), worldSystem.getCurrentTime());
		}

		// Add new containment relationship
		relationshipSystem.add(container, item, relationshipSystem.rvContains);

		client.sendOutput(CommandOutput.make(PUT)
				.put(ItemSystem.M_ITEM, item.getKeyId())
				.put(ItemSystem.M_ITEM_NAME, itemName)
				.put(RelationshipSystem.M_CONTAINER, container.getKeyId())
				.put(RelationshipSystem.M_CONTAINER_NAME, containerName)
				.text(Markup.concat(
						Markup.raw("You put "),
						Markup.em(itemName),
						Markup.raw(" in "),
						Markup.em(containerName),
						Markup.raw("."))));
	}
}
