package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
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
import com.benleskey.textengine.util.Markup;
import com.benleskey.textengine.util.Message;
import com.benleskey.textengine.util.RawMessage;

import java.util.List;
import java.util.Map;
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
		
		// Use fuzzy matching to find the exit (same as navigation)
		String matchedExitName = matchExitName(target, exits);
		
		if (matchedExitName != null) {
			// Find the actual exit descriptor with the matched name
			ConnectionDescriptor matchingExit = exits.stream()
				.filter(e -> e.getExitName().equals(matchedExitName))
				.findFirst()
				.orElse(null);
			
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
					
					// Show what's visible from there (landmarks)
					if (!destExits.isEmpty()) {
						java.util.List<Markup.Safe> landmarkNames = new java.util.ArrayList<>();
						for (ConnectionDescriptor destExit : destExits) {
							landmarkNames.add(Markup.raw(destExit.getExitName()));
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
		
		// Group visible entities by distance
		Map<VisibilitySystem.VisibilityLevel, List<VisibilityDescriptor>> byDistance = 
			visible.stream().collect(Collectors.groupingBy(VisibilityDescriptor::getDistanceLevel));
		
		// Get looks for all visible entities
		Map<Entity, List<LookDescriptor>> nearbyLooks = byDistance
			.getOrDefault(VisibilitySystem.VisibilityLevel.NEARBY, List.of())
			.stream()
			.map(VisibilityDescriptor::getEntity)
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
		
		client.sendOutput(buildEnhancedLookOutput(locationLooks, exits, nearbyLooks, distantLooks));
	}

	private CommandOutput buildEnhancedLookOutput(
		List<LookDescriptor> locationLooks,
		List<ConnectionDescriptor> exits,
		Map<Entity, List<LookDescriptor>> nearbyLooks,
		Map<Entity, List<LookDescriptor>> distantLooks
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

		// Build visible places from exits - show landmarks
		if (!exits.isEmpty()) {
			RawMessage exitMessage = Message.make();
			java.util.List<Markup.Safe> landmarkNames = new java.util.ArrayList<>();
			
			for (ConnectionDescriptor exit : exits) {
				// The exit name IS the landmark/description
				String landmarkName = exit.getExitName();
				landmarkNames.add(Markup.raw(landmarkName));
				exitMessage.put(exit.getExitName(), exit.getTo().getKeyId());
			}
			
			// Build natural language description
			if (!landmarkNames.isEmpty()) {
				// Join with commas and "and"
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
				
				parts.add(Markup.concat(
					Markup.raw(" You can see "),
					Markup.concat(joinedLandmarks.toArray(new Markup.Safe[0])),
					Markup.raw(".")
				));
			}
			output.put(M_LOOK_EXITS, exitMessage);
		}

		// Build nearby section
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

		// Build distant section  
		if (!distantLooks.isEmpty()) {
			java.util.List<Markup.Safe> distantParts = new java.util.ArrayList<>();
			RawMessage distantEntities = Message.make();
			
			for (Map.Entry<Entity, List<LookDescriptor>> entry : distantLooks.entrySet()) {
				List<LookDescriptor> looks = entry.getValue();
				
				for (LookDescriptor lookDescriptor : looks) {
					distantParts.add(Markup.em(lookDescriptor.getDescription()));
				}
			}
			
			if (!distantParts.isEmpty()) {
				java.util.List<Markup.Safe> joined = new java.util.ArrayList<>();
				for (int i = 0; i < distantParts.size(); i++) {
					if (i > 0) joined.add(Markup.raw(", "));
					joined.add(distantParts.get(i));
				}
				
				parts.add(Markup.concat(
					Markup.raw(" In the distance you can see "),
					Markup.concat(joined.toArray(new Markup.Safe[0])),
					Markup.raw(".")
				));
			}
			output.put(M_LOOK_DISTANT, distantEntities);
		}

		// Combine all parts and set as text
		output.text(Markup.concat(parts.toArray(new Markup.Safe[0])));
		return output;
	}
	
	/**
	 * Match user input against available exit names.
	 * Returns the matched exit name, or null if no match or ambiguous.
	 * 
	 * Matching rules:
	 * 1. Exact match (case-insensitive, ignoring markup)
	 * 2. Substring match (case-insensitive, ignoring markup)
	 * 
	 * If multiple exits match, returns null (ambiguous).
	 */
	private String matchExitName(String userInput, List<ConnectionDescriptor> exits) {
		if (exits.isEmpty()) {
			return null;
		}
		
		String lowerInput = userInput.toLowerCase().trim();
		String matchedExit = null;
		int matchCount = 0;
		
		// First pass: try exact match
		for (var exit : exits) {
			String exitName = exit.getExitName();
			String exitNameStripped = stripMarkup(exitName).toLowerCase();
			
			if (exitNameStripped.equals(lowerInput)) {
				return exitName; // Exact match, use it immediately
			}
		}
		
		// Second pass: try substring match
		for (var exit : exits) {
			String exitName = exit.getExitName();
			String exitNameStripped = stripMarkup(exitName).toLowerCase();
			
			if (exitNameStripped.contains(lowerInput)) {
				matchedExit = exitName;
				matchCount++;
			}
		}
		
		// Return matched exit only if unambiguous
		return matchCount == 1 ? matchedExit : null;
	}
	
	/**
	 * Strip markup tags from a string (simple implementation).
	 */
	private String stripMarkup(String text) {
		if (text == null) {
			return "";
		}
		// Remove <em> tags and other markup
		return text.replaceAll("<[^>]+>", "");
	}
}
