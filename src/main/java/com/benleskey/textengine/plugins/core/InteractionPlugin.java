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
import com.benleskey.textengine.plugins.procgen1.systems.ProceduralWorldPlugin;
import com.benleskey.textengine.model.ConnectionDescriptor;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.VisibilitySystem;
import com.benleskey.textengine.systems.ConnectionSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.systems.SpatialSystem;
import com.benleskey.textengine.systems.ItemSystem;
import com.benleskey.textengine.util.Markup;
import com.benleskey.textengine.util.Message;
import com.benleskey.textengine.util.RawMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InteractionPlugin extends Plugin implements OnPluginInitialize {
	public static final String LOOK = "look";
	public static final String LOOK_WITHOUT_ARGUMENTS = "look_without_arguments";
	public static final String LOOK_AT_TARGET = "look_at_target";
	public static final String M_LOOK = "look";
	public static final String M_LOOK_TARGET = "look_target";
	public static final String M_LOOK_ENTITIES = "look_entities";
	public static final String M_LOOK_ENTITY_LOOKS = "look_entity_looks";
	public static final String M_LOOK_TYPE = "look_type";
	public static final String M_LOOK_DESCRIPTION = "look_description";
	public static final String M_LOOK_EXITS = "look_exits";
	public static final String M_LOOK_NEARBY = "look_nearby";
	public static final String M_LOOK_DISTANT = "look_distant";

	// System fields
	private RelationshipSystem relationshipSystem;
	private WorldSystem worldSystem;
	private LookSystem lookSystem;
	private ConnectionSystem connectionSystem;
	private VisibilitySystem visibilitySystem;
	private DisambiguationSystem disambiguationSystem;
	private SpatialSystem spatialSystem;
	private EntityDescriptionSystem entityDescriptionSystem;
	private ActionSystem actorActionSystem;
	private ItemSystem itemSystem;

	public InteractionPlugin(Game game) {
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
		// Initialize system fields
		relationshipSystem = game.getSystem(RelationshipSystem.class);
		worldSystem = game.getSystem(WorldSystem.class);
		lookSystem = game.getSystem(LookSystem.class);
		connectionSystem = game.getSystem(ConnectionSystem.class);
		visibilitySystem = game.getSystem(VisibilitySystem.class);
		disambiguationSystem = game.getSystem(DisambiguationSystem.class);
		spatialSystem = game.getSystem(SpatialSystem.class);
		entityDescriptionSystem = game.getSystem(EntityDescriptionSystem.class);
		actorActionSystem = game.getSystem(ActionSystem.class);
		itemSystem = game.getSystem(ItemSystem.class);

		game.registerCommand(new Command(LOOK, (client, input) -> {
			Entity entity = client.getEntity().orElse(null);
			if (entity != null) {
				// Get current location
				var containers = relationshipSystem.getProvidingRelationships(entity, relationshipSystem.rvContains,
						worldSystem.getCurrentTime());

				if (!containers.isEmpty()) {
					Entity currentLocation = containers.get(0).getProvider();

					// Check if looking at a specific target
					java.util.Optional<Object> targetOpt = input.getO(M_LOOK_TARGET);
					if (targetOpt.isPresent()) {
						// Look at specific direction/place
						String target = targetOpt.get().toString().toLowerCase();
						lookAtTarget(client, currentLocation, target);
					} else {
						// Look at current location (normal look)
						performNormalLook(client, entity, currentLocation);
					}
				} else {
					// Entity has no location
					client.sendOutput(CommandOutput.make(M_LOOK)
							.text(Markup.escape("You are nowhere. This should not happen.")));
				}
			} else {
				client.sendOutput(Client.NO_ENTITY);
			}
		},
				new CommandVariant(LOOK_AT_TARGET, "^look\\s+(?:at\\s+)?(.+?)\\s*$",
						args -> CommandInput.makeNone().put(M_LOOK_TARGET, args.group(1))),
				new CommandVariant(LOOK_WITHOUT_ARGUMENTS, "^look([^\\w]+|$)", args -> CommandInput.makeNone())));
	}

	/**
	 * Look at a specific target (item, entity, exit, or distant landmark).
	 */
	private void lookAtTarget(Client client, Entity currentLocation, String target) {

		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get items in inventory
		List<Entity> carriedItems = relationshipSystem
				.getReceivingRelationships(actor, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(com.benleskey.textengine.model.RelationshipDescriptor::getReceiver)
				.filter(e -> e != actor)
				.collect(Collectors.toList());

		// Get items and entities at current location
		List<Entity> entitiesHere = relationshipSystem
				.getReceivingRelationships(currentLocation, relationshipSystem.rvContains,
						worldSystem.getCurrentTime())
				.stream()
				.map(com.benleskey.textengine.model.RelationshipDescriptor::getReceiver)
				.filter(e -> e != actor)
				.collect(Collectors.toList());

		// Get available exits from current location
		List<ConnectionDescriptor> exits = connectionSystem.getConnections(currentLocation,
				worldSystem.getCurrentTime());

		// Get distant landmarks visible from here
		List<Entity> distantLandmarks = visibilitySystem.getVisibleEntities(actor).stream()
				.filter(vd -> vd.getDistanceLevel() == VisibilitySystem.VisibilityLevel.DISTANT)
				.map(vd -> vd.getEntity())
				.toList();

		// Combine all possible targets for matching (items, entities, exits, landmarks)
		List<Entity> allTargets = new java.util.ArrayList<>();
		allTargets.addAll(carriedItems);
		allTargets.addAll(entitiesHere);
		List<Entity> exitDestinations = exits.stream()
				.map(ConnectionDescriptor::getTo)
				.toList();
		allTargets.addAll(exitDestinations);
		allTargets.addAll(distantLandmarks);

		// Use DisambiguationSystem to resolve the target
		java.util.function.Function<Entity, String> descExtractor = entity -> entityDescriptionSystem
				.getSimpleDescription(entity, worldSystem.getCurrentTime());

		DisambiguationSystem.ResolutionResult<Entity> result = disambiguationSystem.resolveEntityWithAmbiguity(
				client,
				target,
				allTargets,
				descExtractor);

		if (result.isNotFound()) {
			// No matching target found
			client.sendOutput(CommandOutput.make(M_LOOK).text(
					Markup.concat(
							Markup.raw("You don't see anything called "),
							Markup.em(target),
							Markup.raw(" from here."))));
			return;
		}

		if (result.isAmbiguous()) {
			// Handle ambiguous match - show numbered list for disambiguation
			handleAmbiguousMatch(client, M_LOOK, target, result.getAmbiguousMatches(), descExtractor);
			return;
		}

		Entity matchedTarget = result.getUniqueMatch();

		// Check what type of target this is
		boolean isCarriedItem = carriedItems.contains(matchedTarget);
		boolean isNearbyEntity = entitiesHere.contains(matchedTarget);
		boolean isDistantLandmark = distantLandmarks.contains(matchedTarget);

		// Handle examining items or nearby entities
		if (isCarriedItem || isNearbyEntity) {
			examineEntity(client, matchedTarget);
			return;
		}

		// Handle exits and distant landmarks

		if (isDistantLandmark) {
			// Looking at a distant landmark
			String landmarkDescription = entityDescriptionSystem.getSimpleDescription(matchedTarget,
					worldSystem.getCurrentTime(), "something");

			// Find the nearest exit that moves toward this landmark using spatial
			// pathfinding
			Entity closestExit = spatialSystem.findClosestToTarget(
					SpatialSystem.SCALE_CONTINENT, exitDestinations, matchedTarget);

			// Build the message
			java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();

			parts.add(Markup.concat(
					Markup.raw("In the distance, you see "),
					Markup.em(landmarkDescription),
					Markup.raw(".")));

			// Show which exit to take to get closer
			if (closestExit != null) {
				String exitDescription = entityDescriptionSystem.getSimpleDescription(closestExit,
						worldSystem.getCurrentTime());
				parts.add(Markup.concat(
						Markup.raw(" To get closer, head toward "),
						Markup.em(exitDescription),
						Markup.raw(".")));
			}
			client.sendOutput(CommandOutput.make(M_LOOK)
					.put(M_LOOK_TARGET, matchedTarget.getKeyId())
					.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
		} else {
			// Looking at an adjacent exit
			String description = entityDescriptionSystem.getSimpleDescription(matchedTarget,
					worldSystem.getCurrentTime(), "nothing");

			// Get exits from the destination (look ahead)
			List<ConnectionDescriptor> destExits = connectionSystem.getConnections(matchedTarget,
					worldSystem.getCurrentTime());

			// Build the message
			java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();

			// Main description - just show what we see there
			parts.add(Markup.concat(
					Markup.raw("You see "),
					Markup.escape(description),
					Markup.raw(".")));

			// Show what's visible from there (exits to other places)
			if (!destExits.isEmpty()) {
				java.util.List<Markup.Safe> landmarkNames = new java.util.ArrayList<>();
				for (ConnectionDescriptor destExit : destExits) {
					String destExitDesc = entityDescriptionSystem.getSimpleDescription(destExit.getTo(),
							worldSystem.getCurrentTime());
					landmarkNames.add(Markup.raw(destExitDesc));
				}

				// Join landmark names with commas and "and"
				java.util.List<Markup.Safe> joinedLandmarks = new java.util.ArrayList<>();
				for (int i = 0; i < landmarkNames.size(); i++) {
					if (i > 0) {
						if (i == landmarkNames.size() - 1) {
							joinedLandmarks.add(Markup.raw(", and "));
						} else {
							joinedLandmarks.add(Markup.raw(", "));
						}
					}
					joinedLandmarks.add(landmarkNames.get(i));
				}

				parts.add(Markup.raw(" From there you can see "));
				parts.add(Markup.concat(joinedLandmarks.toArray(new Markup.Safe[0])));
				parts.add(Markup.raw("."));
			}

			client.sendOutput(CommandOutput.make(M_LOOK)
					.put(M_LOOK_TARGET, matchedTarget.getKeyId())
					.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
		}
	}

	/**
	 * Perform a normal look at the current location.
	 * Uses LookSystem.getLookEnvironment() for consistent observation logic with
	 * NPCs.
	 */
	private void performNormalLook(Client client, Entity entity, Entity currentLocation) {

		// Use LookSystem's shared environment observation (same as NPCs)
		LookSystem.LookEnvironment env = lookSystem.getLookEnvironment(entity);
		if (env == null) {
			client.sendOutput(CommandOutput.make(M_LOOK)
					.text(Markup.escape("You are nowhere. This should not happen.")));
			return;
		}

		// Get exits as ConnectionDescriptors (for compatibility with existing output
		// builder)
		List<ConnectionDescriptor> exits = connectionSystem.getConnections(currentLocation,
				worldSystem.getCurrentTime());

		// Convert environment data to the format expected by buildEnhancedLookOutput
		// nearbyLooks: non-item entities (actors, etc.)
		Map<Entity, List<LookDescriptor>> nearbyLooks = env.actorsHere.stream()
				.collect(Collectors.toMap(
						e -> e,
						e -> lookSystem.getLooksFromEntity(e, worldSystem.getCurrentTime())));

		// itemLooks: items at location (already excludes containers in some contexts)
		Map<Entity, List<LookDescriptor>> itemLooks = env.itemsHere.stream()
				.collect(Collectors.toMap(
						e -> e,
						e -> lookSystem.getLooksFromEntity(e, worldSystem.getCurrentTime())));

		// distantLooks: landmarks visible from distance
		Map<Entity, List<LookDescriptor>> distantLooks = env.distantLandmarks.stream()
				.collect(Collectors.toMap(
						e -> e,
						e -> lookSystem.getLooksFromEntity(e, worldSystem.getCurrentTime())));

		client.sendOutput(
				buildEnhancedLookOutput(client, env.locationLooks, exits, nearbyLooks, itemLooks, distantLooks));
	}

	private CommandOutput buildEnhancedLookOutput(
			Client client,
			List<LookDescriptor> locationLooks,
			List<ConnectionDescriptor> exits,
			Map<Entity, List<LookDescriptor>> nearbyLooks,
			Map<Entity, List<LookDescriptor>> itemLooks,
			Map<Entity, List<LookDescriptor>> distantLooks) {

		CommandOutput output = CommandOutput.make(M_LOOK);
		java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();

		// Show current location description
		if (!locationLooks.isEmpty()) {
			java.util.List<Markup.Safe> locationParts = new java.util.ArrayList<>();
			for (LookDescriptor look : locationLooks) {
				locationParts.add(Markup.escape(look.getDescription()));
			}
			parts.add(Markup.concat(
					Markup.raw("You are in "),
					Markup.concat(locationParts.toArray(new Markup.Safe[0])),
					Markup.raw(".")));
		}

		// Build visible places from exits (no numeric IDs)
		if (!exits.isEmpty()) {
			RawMessage exitMessage = Message.make();

			// Build machine-readable exit data
			for (ConnectionDescriptor exit : exits) {
				String description = entityDescriptionSystem.getSimpleDescription(exit.getTo(),
						worldSystem.getCurrentTime(), "unknown");
				exitMessage.put(description, exit.getTo().getKeyId());
			}

			// Format the list for display (no numeric IDs)
			java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
			for (int i = 0; i < exits.size(); i++) {
				if (i > 0) {
					if (i == exits.size() - 1 && exits.size() > 1) {
						joined.add(Markup.raw(", and "));
					} else if (exits.size() > 1) {
						joined.add(Markup.raw(", "));
					}
				}

				List<LookDescriptor> looks = lookSystem.getLooksFromEntity(exits.get(i).getTo(),
						worldSystem.getCurrentTime());
				String description = !looks.isEmpty() ? looks.get(0).getDescription() : "unknown";
				joined.add(Markup.em(description));
			}

			parts.add(Markup.concat(
					Markup.raw(" You can see "),
					Markup.concat(joined.toArray(new Markup.Safe[0])),
					Markup.raw(".")));

			output.put(M_LOOK_EXITS, exitMessage);
		}

		// Build distant landmarks section (no numeric IDs)
		if (!distantLooks.isEmpty()) {
			RawMessage distantEntities = Message.make();

			// Extract landmarks and their descriptions
			List<Entity> landmarks = new java.util.ArrayList<>(distantLooks.keySet());

			// Build machine-readable landmark data
			for (Entity landmark : landmarks) {
				List<LookDescriptor> looks = distantLooks.get(landmark);
				RawMessage entityMessage = Message.make();
				RawMessage entityLooks = Message.make();

				for (LookDescriptor lookDescriptor : looks) {
					RawMessage lookMessage = Message.make()
							.put(M_LOOK_TYPE, lookDescriptor.getType())
							.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
					entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
				}

				entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);
				distantEntities.put(landmark.getKeyId(), entityMessage);
			}

			// Format the list for display (no numeric IDs)
			java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
			for (int i = 0; i < landmarks.size(); i++) {
				if (i > 0) {
					if (i == landmarks.size() - 1 && landmarks.size() > 1) {
						joined.add(Markup.raw(", and "));
					} else if (landmarks.size() > 1) {
						joined.add(Markup.raw(", "));
					}
				}

				List<LookDescriptor> looks = distantLooks.get(landmarks.get(i));
				if (looks != null && !looks.isEmpty()) {
					joined.add(Markup.em(looks.get(0).getDescription()));
				}
			}

			if (!joined.isEmpty()) {
				parts.add(Markup.concat(
						Markup.raw(" In the distance you can see "),
						Markup.concat(joined.toArray(new Markup.Safe[0])),
						Markup.raw(".")));
			}
			output.put(M_LOOK_DISTANT, distantEntities);
		}

		// Build items section (no numeric IDs)
		if (!itemLooks.isEmpty()) {
			RawMessage itemEntities = Message.make();

			// Extract items and their descriptions
			List<Entity> items = new java.util.ArrayList<>(itemLooks.keySet());

			// Build entity message for machine-readable API
			for (Entity item : items) {
				List<LookDescriptor> looks = itemLooks.get(item);
				RawMessage entityMessage = Message.make();
				RawMessage entityLooks = Message.make();

				for (LookDescriptor lookDescriptor : looks) {
					RawMessage lookMessage = Message.make()
							.put(M_LOOK_TYPE, lookDescriptor.getType())
							.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
					entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
				}

				entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);

				// Add container contents if this item is a container
				if (item instanceof Item) {
					List<com.benleskey.textengine.model.RelationshipDescriptor> itemContents = relationshipSystem
							.getReceivingRelationships(item, relationshipSystem.rvContains,
									worldSystem.getCurrentTime());

					if (!itemContents.isEmpty()) {
						List<Map<String, Object>> contentsList = new java.util.ArrayList<>();

						for (com.benleskey.textengine.model.RelationshipDescriptor rd : itemContents) {
							Entity contentItem = rd.getReceiver();
							if (contentItem instanceof Item) {
								String contentDesc = entityDescriptionSystem.getSimpleDescription(contentItem,
										worldSystem.getCurrentTime(), "something");

								Map<String, Object> contentData = new java.util.HashMap<>();
								contentData.put("entity_id", contentItem.getKeyId());
								contentData.put("item_name", contentDesc);
								contentsList.add(contentData);
							}
						}
						if (!contentsList.isEmpty()) {
							entityMessage.put("container_contents", contentsList);
						}
					}
				}

				itemEntities.put(item.getKeyId(), entityMessage);
			}

			// Format the list for display (no numeric IDs)
			java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				if (i > 0) {
					if (i == items.size() - 1 && items.size() > 1) {
						joined.add(Markup.raw(", and "));
					} else if (items.size() > 1) {
						joined.add(Markup.raw(", "));
					}
				}

				List<LookDescriptor> looks = itemLooks.get(items.get(i));
				if (looks != null && !looks.isEmpty()) {
					joined.add(Markup.em(looks.get(0).getDescription()));
				}
			}

			if (!joined.isEmpty()) {
				parts.add(Markup.raw("\nItems: "));
				parts.add(Markup.concat(joined.toArray(new Markup.Safe[0])));
			}

			output.put("look_items", itemEntities);
		}

		// Build nearby section (actors and other non-item entities)
		if (!nearbyLooks.isEmpty()) {
			java.util.List<Markup.Safe> nearbyParts = new java.util.ArrayList<>();
			RawMessage nearbyEntities = Message.make();

			for (Map.Entry<Entity, List<LookDescriptor>> entry : nearbyLooks.entrySet()) {
				Entity entity = entry.getKey();
				List<LookDescriptor> looks = entry.getValue();

				RawMessage entityMessage = Message.make();
				RawMessage entityLooks = Message.make();
				java.util.List<Markup.Safe> entityParts = new java.util.ArrayList<>();

				for (LookDescriptor lookDescriptor : looks) {
					RawMessage lookMessage = Message.make()
							.put(M_LOOK_TYPE, lookDescriptor.getType())
							.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
					entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
					entityParts.add(Markup.escape(lookDescriptor.getDescription()));
				}

				entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);

				// Check if this actor has a pending action
				String pendingAction = null;
				if (entity instanceof com.benleskey.textengine.entities.Actor) {
					pendingAction = actorActionSystem
							.getPendingActionDescription((com.benleskey.textengine.entities.Actor) entity);
					if (pendingAction != null) {
						entityMessage.put("pending_action", pendingAction);
					}
				}

				// Join entity looks with commas
				java.util.List<Markup.Safe> joinedEntity = new java.util.ArrayList<>();
				for (int i = 0; i < entityParts.size(); i++) {
					if (i > 0)
						joinedEntity.add(Markup.raw(", "));
					joinedEntity.add(entityParts.get(i));
				}

				// Add pending action to display
				if (pendingAction != null) {
					nearbyParts.add(Markup.concat(
							Markup.concat(joinedEntity.toArray(new Markup.Safe[0])),
							Markup.raw(" ("),
							Markup.escape(pendingAction),
							Markup.raw(")")));
				} else {
					nearbyParts.add(Markup.concat(joinedEntity.toArray(new Markup.Safe[0])));
				}

				nearbyEntities.put(entity.getKeyId(), entityMessage);
			}

			if (!nearbyParts.isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				for (int i = 0; i < nearbyParts.size(); i++) {
					if (i > 0)
						joined.add(Markup.raw(", "));
					joined.add(nearbyParts.get(i));
				}

				parts.add(Markup.concat(
						Markup.raw("\nNearby: "),
						Markup.concat(joined.toArray(new Markup.Safe[0])),
						Markup.raw(".")));
			}
			output.put(M_LOOK_NEARBY, nearbyEntities);
		}

		// Combine all parts and set as text
		output.text(Markup.concat(parts.toArray(new Markup.Safe[0])));
		return output;
	}

	/**
	 * Examine a specific entity (item or actor) in detail.
	 * Provides tag-based descriptions, weight, dynamic descriptions, and container
	 * contents.
	 */
	private void examineEntity(Client client, Entity targetEntity) {
		// Get entity description
		String entityName = entityDescriptionSystem.getSimpleDescription(targetEntity, worldSystem.getCurrentTime(),
				"something");

		// Build examination output - all on one line
		java.util.List<Markup.Safe> examineMarkup = new java.util.ArrayList<>();
		examineMarkup.add(Markup.raw("You examine "));
		examineMarkup.add(Markup.raw(entityName));
		examineMarkup.add(Markup.raw(". "));

		// Add tag-based descriptions on same line with space separation
		List<String> tagDescriptions = entityDescriptionSystem.getTagDescriptions(targetEntity,
				worldSystem.getCurrentTime());
		for (int i = 0; i < tagDescriptions.size(); i++) {
			if (i > 0) {
				examineMarkup.add(Markup.raw(" ")); // Space between descriptions
			}
			examineMarkup.add(Markup.escape(tagDescriptions.get(i)));
		}

		// Add weight if present
		Long weightGrams = itemSystem.getTagValue(targetEntity, itemSystem.TAG_WEIGHT, worldSystem.getCurrentTime());
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
				.getReceivingRelationships(targetEntity, relationshipSystem.rvContains, worldSystem.getCurrentTime())
				.stream()
				.map(com.benleskey.textengine.model.RelationshipDescriptor::getReceiver)
				.filter(e -> e instanceof Item)
				.collect(Collectors.toList());

		// Check if entity provides dynamic description
		if (targetEntity instanceof com.benleskey.textengine.entities.DynamicDescription dynamicDesc) {
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
			itemData.put("entity_id", item.getKeyId());
			itemData.put("item_name", desc);
			itemsList.add(itemData);
		}

		client.sendOutput(CommandOutput.make(M_LOOK)
				.put("entity_id", String.valueOf(targetEntity.getId()))
				.put(M_LOOK_TARGET, targetEntity)
				.put("items", itemsList)
				.text(Markup.concat(examineMarkup.toArray(new Markup.Safe[0]))));
	}

	/**
	 * Handle ambiguous matches by delegating to DisambiguationSystem.
	 */
	private <T extends Entity> void handleAmbiguousMatch(
			Client client,
			String commandId,
			String userInput,
			List<T> matches,
			java.util.function.Function<T, String> descriptionExtractor) {

		disambiguationSystem.sendDisambiguationPrompt(client, commandId, userInput, matches, descriptionExtractor);
	}
}
