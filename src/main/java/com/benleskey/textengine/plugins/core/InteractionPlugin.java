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
import com.benleskey.textengine.util.Message;
import com.benleskey.textengine.util.RawMessage;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class InteractionPlugin extends Plugin implements OnPluginInitialize {
	public static final String LOOK = "look";
	public static final String LOOK_WITHOUT_ARGUMENTS = "look_without_arguments";
	public static final String M_LOOK = "look";
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

		output.text(overallText.toString());
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
					
					// Get current location description
					List<LookDescriptor> locationLooks = ls.getLooksFromEntity(currentLocation, ws.getCurrentTime());
					
					// Get exits
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
				} else {
					client.sendOutput(buildLookOutput(ls.getSeenLooks(entity).stream()
						.collect(Collectors.groupingBy(LookDescriptor::getEntity))));
				}
			} else {
				client.sendOutput(Client.NO_ENTITY);
			}
		}, new CommandVariant(LOOK_WITHOUT_ARGUMENTS, "^look([^\\w]+|$)", args -> CommandInput.makeNone())));
	}

	private CommandOutput buildEnhancedLookOutput(
		List<LookDescriptor> locationLooks,
		List<ConnectionDescriptor> exits,
		Map<Entity, List<LookDescriptor>> nearbyLooks,
		Map<Entity, List<LookDescriptor>> distantLooks
	) {
		CommandOutput output = CommandOutput.make(M_LOOK);
		StringJoiner overallText = new StringJoiner("\n\n");

		// Show current location description
		if (!locationLooks.isEmpty()) {
			StringJoiner locationText = new StringJoiner(", ");
			for (LookDescriptor look : locationLooks) {
				locationText.add(look.getDescription());
			}
			overallText.add("You are in " + locationText + ".");
		}

		// Build nearby section
		if (!nearbyLooks.isEmpty()) {
			StringJoiner nearbyText = new StringJoiner(", ");
			RawMessage nearbyEntities = Message.make();
			
			for (Map.Entry<Entity, List<LookDescriptor>> entry : nearbyLooks.entrySet()) {
				Entity entity = entry.getKey();
				List<LookDescriptor> looks = entry.getValue();
				
				RawMessage entityMessage = Message.make();
				RawMessage entityLooks = Message.make();
				StringJoiner entityText = new StringJoiner(", ");

				for (LookDescriptor lookDescriptor : looks) {
					RawMessage lookMessage = Message.make()
						.put(M_LOOK_TYPE, lookDescriptor.getType())
						.put(M_LOOK_DESCRIPTION, lookDescriptor.getDescription());
					entityLooks.put(lookDescriptor.getLook().getKeyId(), lookMessage);
					entityText.add(lookDescriptor.getDescription());
				}

				entityMessage.put(M_LOOK_ENTITY_LOOKS, entityLooks);
				nearbyText.add(entityText.toString());
				nearbyEntities.put(entity.getKeyId(), entityMessage);
			}
			
			if (nearbyText.length() > 0) {
				overallText.add("Nearby: " + nearbyText);
			}
			output.put(M_LOOK_NEARBY, nearbyEntities);
		}

		// Build distant section  
		if (!distantLooks.isEmpty()) {
			StringJoiner distantText = new StringJoiner(", ");
			RawMessage distantEntities = Message.make();
			
			for (Map.Entry<Entity, List<LookDescriptor>> entry : distantLooks.entrySet()) {
				Entity entity = entry.getKey();
				List<LookDescriptor> looks = entry.getValue();
				
				StringJoiner entityText = new StringJoiner(", ");
				for (LookDescriptor lookDescriptor : looks) {
					entityText.add(lookDescriptor.getDescription());
				}
				
				distantText.add(entityText.toString());
			}
			
			if (distantText.length() > 0) {
				overallText.add("In the distance: " + distantText);
			}
			output.put(M_LOOK_DISTANT, distantEntities);
		}

		// Build exits section
		if (!exits.isEmpty()) {
			StringJoiner exitText = new StringJoiner("\n");
			RawMessage exitMessage = Message.make();
			
			for (ConnectionDescriptor exit : exits) {
				// Get the looks for the destination
				Entity destination = exit.getTo();
				List<LookDescriptor> destLooks = game.getSystem(LookSystem.class)
					.getLooksFromEntity(destination, game.getSystem(WorldSystem.class).getCurrentTime());
				
				String destDescription = destLooks.isEmpty() 
					? exit.getExitName() 
					: destLooks.get(0).getDescription();
				
				exitText.add(String.format("  %s: %s", exit.getExitName(), destDescription));
				exitMessage.put(exit.getExitName(), exit.getTo().getKeyId());
			}
			
			overallText.add("Exits:\n" + exitText);
			output.put(M_LOOK_EXITS, exitMessage);
		}

		output.text(overallText.toString());
		return output;
	}
}
