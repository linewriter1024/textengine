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
import com.benleskey.textengine.model.VisibilityDescriptor;
import com.benleskey.textengine.model.ConnectionDescriptor;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.VisibilitySystem;
import com.benleskey.textengine.systems.ConnectionSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.util.FuzzyMatcher;
import com.benleskey.textengine.util.Markup;
import com.benleskey.textengine.util.Message;
import com.benleskey.textengine.util.RawMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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

	public InteractionPlugin(Game game) {
		super(game);
	}

	private CommandOutput buildLookOutput(Map<Entity, List<LookDescriptor>> groupedLooks) {
		CommandOutput output = CommandOutput.make(M_LOOK);
		StringJoiner overallText = new StringJoiner("\n");

		RawMessage entities = Message.make();
		for (Entity entity : groupedLooks.keySet()) {

			RawMessage entityMessage = Message.make();
			RawMessage entityLooks = Message.make();
			StringJoiner entityText = new StringJoiner(", ");

			for (LookDescriptor lookDescriptor : groupedLooks.get(entity)) {
				RawMessage lookMessage = Message.make()
					.put(M_LOOK_TYPE, lookDescriptor.getType())
					.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
				entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
				entityText.add(lookDescriptor.getDescription());
			}

			entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);

			overallText.add(entityText.toString());
			entities.put(entity.getKeyId(), entityMessage);
		}

		output.text(Markup.escape(overallText.toString()));
		output.put(M_LOOK_ENTITIES, entities);

		return output;
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(LOOK, (client, input) -> {
			Entity entity = client.getEntity().orElse(null);
			if (entity != null) {
				LookSystem ls = game.getSystem(LookSystem.class);
				VisibilitySystem vs = game.getSystem(VisibilitySystem.class);
				ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
				RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
				WorldSystem ws = game.getSystem(WorldSystem.class);

				// Get current location
				var containers = rs.getProvidingRelationships(entity, rs.rvContains, ws.getCurrentTime());
				
				if (!containers.isEmpty()) {
					Entity currentLocation = containers.get(0).getProvider();
					
					// Check if looking at a specific target
					java.util.Optional<Object> targetOpt = input.getO(M_LOOK_TARGET);
					if (targetOpt.isPresent()) {
						// Look at specific direction/place
						String target = targetOpt.get().toString().toLowerCase();
						lookAtTarget(client, currentLocation, target, ls, cs, ws);
					} else {
						// Look at current location (normal look)
						performNormalLook(client, entity, currentLocation, ls, vs, cs, rs, ws);
					}
				} else {
					client.sendOutput(buildLookOutput(ls.getSeenLooks(entity).stream()
						.collect(Collectors.groupingBy(LookDescriptor::getEntity))));
				}
			} else {
				client.sendOutput(Client.NO_ENTITY);
			}
		}, 
		new CommandVariant(LOOK_AT_TARGET, "^look\\s+(?:at\\s+)?(.+?)\\s*$", args -> 
			CommandInput.makeNone().put(M_LOOK_TARGET, args.group(1))),
		new CommandVariant(LOOK_WITHOUT_ARGUMENTS, "^look([^\\w]+|$)", args -> CommandInput.makeNone())));
	}
	
	/**
	 * Look at a specific target (direction or place name).
	 */
	private void lookAtTarget(Client client, Entity currentLocation, String target, 
			LookSystem ls, ConnectionSystem cs, WorldSystem ws) {
		
		// Get available exits from current location
		List<ConnectionDescriptor> exits = cs.getConnections(currentLocation, ws.getCurrentTime());
		
		// Extract destination descriptions for fuzzy matching
		List<String> exitDescriptions = new java.util.ArrayList<>();
		for (ConnectionDescriptor exit : exits) {
			List<LookDescriptor> looks = ls.getLooksFromEntity(exit.getTo(), ws.getCurrentTime());
			if (!looks.isEmpty()) {
				exitDescriptions.add(looks.get(0).getDescription());
			}
		}
		
		// Use fuzzy matching to find the exit description
		String matchedDescription = FuzzyMatcher.match(target, exitDescriptions);
		
		if (matchedDescription != null) {
			// Find the corresponding exit
			ConnectionDescriptor matchingExit = null;
			for (ConnectionDescriptor exit : exits) {
				List<LookDescriptor> looks = ls.getLooksFromEntity(exit.getTo(), ws.getCurrentTime());
				if (!looks.isEmpty() && looks.get(0).getDescription().equals(matchedDescription)) {
					matchingExit = exit;
					break;
				}
			}
			
			if (matchingExit != null) {
				// Found an exit, show description of destination
				Entity destination = matchingExit.getTo();
				List<LookDescriptor> destLooks = ls.getLooksFromEntity(destination, ws.getCurrentTime());
					
				if (!destLooks.isEmpty()) {
					String description = destLooks.get(0).getDescription();
					
					// Get exits from the destination (look ahead)
					List<ConnectionDescriptor> destExits = cs.getConnections(destination, ws.getCurrentTime());
					
					// Build the message
					java.util.List<Markup.Safe> parts = new java.util.ArrayList<>();
					
					// Main description - just show what we see there
					parts.add(Markup.concat(
						Markup.raw("You see "),
						Markup.escape(description),
						Markup.raw(".")
					));
					
					// Show what's visible from there (exits to other places)
					if (!destExits.isEmpty()) {
						java.util.List<Markup.Safe> landmarkNames = new java.util.ArrayList<>();
						for (ConnectionDescriptor destExit : destExits) {
							List<LookDescriptor> destExitLooks = ls.getLooksFromEntity(destExit.getTo(), ws.getCurrentTime());
							if (!destExitLooks.isEmpty()) {
								landmarkNames.add(Markup.raw(destExitLooks.get(0).getDescription()));
							}
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
					.text(Markup.concat(parts.toArray(new Markup.Safe[0]))));
				} else {
					client.sendOutput(CommandOutput.make(M_LOOK)
						.text(Markup.escape("You see nothing notable in that direction.")));
				}
			}
		} else {
			// No matching exit
			client.sendOutput(CommandOutput.make(M_LOOK).text(
				Markup.concat(
					Markup.raw("You don't see anything called "),
					Markup.em(target),
					Markup.raw(" from here.")
				)));
		}
	}
	
	/**
	 * Perform normal look (at current location).
	 */
	private void performNormalLook(Client client, Entity entity, Entity currentLocation,
			LookSystem ls, VisibilitySystem vs, ConnectionSystem cs, 
			RelationshipSystem rs, WorldSystem ws) {
		
		// Get current location description
		List<LookDescriptor> locationLooks = ls.getLooksFromEntity(currentLocation, ws.getCurrentTime());
		
		// Get exits (connections to generated places)
		List<ConnectionDescriptor> exits = cs.getConnections(currentLocation, ws.getCurrentTime());
		
		// Get visible entities
		List<VisibilityDescriptor> visible = vs.getVisibleEntities(entity);
		
		// Get items directly carried by the observer (to exclude from location display)
		Set<Long> carriedItemIds = rs.getReceivingRelationships(entity, rs.rvContains, ws.getCurrentTime())
			.stream()
			.map(rd -> rd.getReceiver().getId())
			.collect(Collectors.toSet());
		
		// Group visible entities by distance
		Map<VisibilitySystem.VisibilityLevel, List<VisibilityDescriptor>> byDistance = 
			visible.stream().collect(Collectors.groupingBy(VisibilityDescriptor::getDistanceLevel));
		
		// Separate items from other entities, excluding items we're carrying
		List<Entity> nearbyItems = byDistance
			.getOrDefault(VisibilitySystem.VisibilityLevel.NEARBY, List.of())
			.stream()
			.map(VisibilityDescriptor::getEntity)
			.filter(e -> e instanceof Item)
			.filter(e -> !carriedItemIds.contains(e.getId()))  // Exclude items we're carrying
			.collect(Collectors.toList());
		
		List<Entity> nearbyNonItems = byDistance
			.getOrDefault(VisibilitySystem.VisibilityLevel.NEARBY, List.of())
			.stream()
			.map(VisibilityDescriptor::getEntity)
			.filter(e -> !(e instanceof Item))
			.collect(Collectors.toList());
		
		// Get looks for all visible entities
		Map<Entity, List<LookDescriptor>> nearbyLooks = nearbyNonItems.stream()
			.collect(Collectors.toMap(
				e -> e,
				e -> ls.getLooksFromEntity(e, ws.getCurrentTime())
			));
		
		Map<Entity, List<LookDescriptor>> itemLooks = nearbyItems.stream()
			.collect(Collectors.toMap(
				e -> e,
				e -> ls.getLooksFromEntity(e, ws.getCurrentTime())
			));
		
		Map<Entity, List<LookDescriptor>> distantLooks = byDistance
			.getOrDefault(VisibilitySystem.VisibilityLevel.DISTANT, List.of())
			.stream()
			.map(VisibilityDescriptor::getEntity)
			.collect(Collectors.toMap(
				e -> e,
				e -> ls.getLooksFromEntity(e, ws.getCurrentTime())
			));
		
		client.sendOutput(buildEnhancedLookOutput(client, locationLooks, exits, nearbyLooks, itemLooks, distantLooks, ls, ws));
	}

	private CommandOutput buildEnhancedLookOutput(
		Client client,
		List<LookDescriptor> locationLooks,
		List<ConnectionDescriptor> exits,
		Map<Entity, List<LookDescriptor>> nearbyLooks,
		Map<Entity, List<LookDescriptor>> itemLooks,
		Map<Entity, List<LookDescriptor>> distantLooks,
		LookSystem ls,
		WorldSystem ws
	) {
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
				Markup.raw(".")
			));
		}

		// Build combined numeric ID map for exits and items
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		Map<Integer, Entity> combinedIdMap = new java.util.HashMap<>();
		int nextId = 1;
		
		// Build visible places from exits with numeric IDs
		DisambiguationSystem.DisambiguatedList exitDisambiguated = null;
		if (!exits.isEmpty()) {
			RawMessage exitMessage = Message.make();
			
			// Build disambiguated list for exits (IDs start at 1)
			exitDisambiguated = ds.buildDisambiguatedList(
				exits.stream().map(ConnectionDescriptor::getTo).collect(Collectors.toList()),
				exit -> {
					// Get the description of the destination place
					List<LookDescriptor> looks = ls.getLooksFromEntity(exit, ws.getCurrentTime());
					return !looks.isEmpty() ? looks.get(0).getDescription() : null;
				}
			);
			
			// Add exit IDs to combined map
			combinedIdMap.putAll(exitDisambiguated.getNumericIdMap());
			nextId = combinedIdMap.size() + 1;
			
			// Build machine-readable exit data
			for (ConnectionDescriptor exit : exits) {
				List<LookDescriptor> looks = ls.getLooksFromEntity(exit.getTo(), ws.getCurrentTime());
				String description = !looks.isEmpty() ? looks.get(0).getDescription() : "unknown";
				exitMessage.put(description, exit.getTo().getKeyId());
			}
			
			// Format the list for display
			if (!exitDisambiguated.getMarkupParts().isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				List<Markup.Safe> markupParts = exitDisambiguated.getMarkupParts();
				for (int i = 0; i < markupParts.size(); i++) {
					if (i > 0) {
						if (i == markupParts.size() - 1 && markupParts.size() > 1) {
							joined.add(Markup.raw(", and "));
						} else if (markupParts.size() > 1) {
							joined.add(Markup.raw(", "));
						}
					}
					joined.add(markupParts.get(i));
				}
				
				parts.add(Markup.concat(
					Markup.raw(" You can see "),
					Markup.concat(joined.toArray(new Markup.Safe[0])),
					Markup.raw(".")
				));
			}
			output.put(M_LOOK_EXITS, exitMessage);
		}
		
		// Build distant landmarks section with numeric IDs (continuing from exits)
		if (!distantLooks.isEmpty()) {
			RawMessage distantEntities = Message.make();
			
			// Extract landmarks and their descriptions
			List<Entity> landmarks = new java.util.ArrayList<>(distantLooks.keySet());
			
			// Build disambiguated list
			DisambiguationSystem.DisambiguatedList landmarkDisambiguated = ds.buildDisambiguatedList(
				landmarks,
				landmark -> {
					List<LookDescriptor> looks = distantLooks.get(landmark);
					return (looks != null && !looks.isEmpty()) ? looks.get(0).getDescription() : null;
				}
			);
			
			// Add landmark IDs to combined map (renumber to continue from exits)
			List<Markup.Safe> correctedLandmarkParts = new java.util.ArrayList<>();
			int currentLandmarkId = nextId;
			for (Entity landmark : landmarks) {
				combinedIdMap.put(currentLandmarkId, landmark);
				List<LookDescriptor> looks = distantLooks.get(landmark);
				if (looks != null && !looks.isEmpty()) {
					String description = looks.get(0).getDescription();
					correctedLandmarkParts.add(Markup.concat(
						Markup.em(description),
						Markup.raw(" [" + currentLandmarkId + "]")
					));
				}
				currentLandmarkId++;
			}
			nextId = currentLandmarkId;
			
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
			
			// Format the list for display
			if (!correctedLandmarkParts.isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				for (int i = 0; i < correctedLandmarkParts.size(); i++) {
					if (i > 0) {
						if (i == correctedLandmarkParts.size() - 1 && correctedLandmarkParts.size() > 1) {
							joined.add(Markup.raw(", and "));
						} else {
							joined.add(Markup.raw(", "));
						}
					}
					joined.add(correctedLandmarkParts.get(i));
				}
				
				parts.add(Markup.concat(
					Markup.raw(" In the distance you can see "),
					Markup.concat(joined.toArray(new Markup.Safe[0])),
					Markup.raw(".")
				));
			}
			output.put(M_LOOK_DISTANT, distantEntities);
		}
		
		// Build items section with numeric IDs (continuing from exits and landmarks)
		DisambiguationSystem.DisambiguatedList itemDisambiguated = null;
		if (!itemLooks.isEmpty()) {
			RawMessage itemEntities = Message.make();
			
			// Extract items and their descriptions
			List<Entity> items = new java.util.ArrayList<>(itemLooks.keySet());
			
			// Build disambiguated list (IDs continue from exits)
			itemDisambiguated = ds.buildDisambiguatedList(
				items,
				item -> {
					List<LookDescriptor> looks = itemLooks.get(item);
					return (looks != null && !looks.isEmpty()) ? looks.get(0).getDescription() : null;
				}
			);
			
			// Add item IDs to combined map (renumber to continue from exits)
			for (Map.Entry<Integer, Entity> entry : itemDisambiguated.getNumericIdMap().entrySet()) {
				combinedIdMap.put(nextId++, entry.getValue());
			}
			
			// Rebuild markup parts with corrected IDs
			List<Markup.Safe> correctedMarkupParts = new java.util.ArrayList<>();
			int currentItemId = combinedIdMap.size() - items.size() + 1;
			for (Entity item : items) {
				List<LookDescriptor> looks = itemLooks.get(item);
				if (looks != null && !looks.isEmpty()) {
					String description = looks.get(0).getDescription();
					correctedMarkupParts.add(Markup.concat(
						Markup.em(description),
						Markup.raw(" [" + currentItemId++ + "]")
					));
				}
			}
			
			// Store combined numeric ID map in client
			ds.setClientMapping(client, combinedIdMap);
			
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
				itemEntities.put(item.getKeyId(), entityMessage);
			}
			
			// Format the list for display using corrected markup parts
			if (!correctedMarkupParts.isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				for (int i = 0; i < correctedMarkupParts.size(); i++) {
					if (i > 0) {
						if (i == correctedMarkupParts.size() - 1 && correctedMarkupParts.size() > 1) {
							joined.add(Markup.raw(", and "));
						} else if (correctedMarkupParts.size() > 1) {
							joined.add(Markup.raw(", "));
						}
					}
					joined.add(correctedMarkupParts.get(i));
				}
				
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
				
				// Join entity looks with commas
				java.util.List<Markup.Safe> joinedEntity = new java.util.ArrayList<>();
				for (int i = 0; i < entityParts.size(); i++) {
					if (i > 0) joinedEntity.add(Markup.raw(", "));
					joinedEntity.add(entityParts.get(i));
				}
				nearbyParts.add(Markup.concat(joinedEntity.toArray(new Markup.Safe[0])));
				nearbyEntities.put(entity.getKeyId(), entityMessage);
			}
			
			if (!nearbyParts.isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				for (int i = 0; i < nearbyParts.size(); i++) {
					if (i > 0) joined.add(Markup.raw(", "));
					joined.add(nearbyParts.get(i));
				}
				
				parts.add(Markup.concat(
					Markup.raw(" Nearby: "),
					Markup.concat(joined.toArray(new Markup.Safe[0])),
					Markup.raw(".")
				));
			}
			output.put(M_LOOK_NEARBY, nearbyEntities);
		}

		// Combine all parts and set as text
		output.text(Markup.concat(parts.toArray(new Markup.Safe[0])));
		return output;
	}
}
