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
import com.benleskey.textengine.systems.ConnectionSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.VisibilitySystem;
import com.benleskey.textengine.systems.WorldSystem;
import com.benleskey.textengine.util.Markup;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

import static com.benleskey.textengine.plugins.core.InteractionPlugin.LOOK;

/**
 * NavigationPlugin handles player movement between locations.
 */
public class NavigationPlugin extends Plugin implements OnPluginInitialize {
	public static final String GO = "go";
	public static final String GO_DIRECTION = "go_direction";
	public static final String M_GO = "go";
	public static final String M_GO_SUCCESS = "go_success";
	public static final String M_GO_FAIL = "go_fail";
	public static final String M_GO_DESTINATION = "destination";
	public static final String M_GO_EXIT = "exit";
	public static final String M_GO_ERROR = "error";

	public NavigationPlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(GO, this::handleGo,
			// Match: go north, go n, go castle, etc.
			new CommandVariant(GO_DIRECTION, "^(?:go|move|travel)\\s+(.+?)\\s*$", this::parseGo)
		));
	}

	private CommandInput parseGo(Matcher matcher) {
		String direction = matcher.group(1).trim().toLowerCase();
		return CommandInput.makeNone().put(M_GO_EXIT, direction);
	}

	private void handleGo(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Get the exit name from input - use getO() which returns Optional
		Optional<Object> exitOptional = input.getO(M_GO_EXIT);
		if (exitOptional.isEmpty()) {
			client.sendOutput(CommandOutput.make(M_GO_FAIL)
				.put(M_GO_ERROR, "no_direction")
				.text(Markup.escape("Where do you want to go?")));
			return;
		}
		
		String userInput = exitOptional.get().toString();

		ConnectionSystem cs = game.getSystem(ConnectionSystem.class);
		RelationshipSystem rs = game.getSystem(RelationshipSystem.class);
		WorldSystem ws = game.getSystem(WorldSystem.class);

		// Find current location (what contains the actor)
		var containers = rs.getProvidingRelationships(actor, rs.rvContains, ws.getCurrentTime());
		if (containers.isEmpty()) {
			client.sendOutput(CommandOutput.make(M_GO_FAIL)
				.put(M_GO_ERROR, "nowhere")
				.text(Markup.escape("You are nowhere. This should not happen.")));
			return;
		}

		Entity currentLocation = containers.get(0).getProvider();
		
		// Get available exits and match user input using DisambiguationSystem
		List<com.benleskey.textengine.model.ConnectionDescriptor> exits = 
			cs.getConnections(currentLocation, ws.getCurrentTime());
		
		// Try numeric ID first, then fuzzy match
		DisambiguationSystem ds = game.getSystem(DisambiguationSystem.class);
		
		// Extract exit destinations as entities
		List<Entity> exitDestinations = exits.stream()
			.map(com.benleskey.textengine.model.ConnectionDescriptor::getTo)
			.toList();
		
		// Also get distant landmarks that might be visible
		VisibilitySystem vs = game.getSystem(VisibilitySystem.class);
		List<Entity> distantLandmarks = vs.getVisibleEntities(actor).stream()
			.filter(vd -> vd.getDistanceLevel() == VisibilitySystem.VisibilityLevel.DISTANT)
			.map(vd -> vd.getEntity())
			.toList();
		
		// Combine exits and landmarks for matching
		List<Entity> allDestinations = new java.util.ArrayList<>(exitDestinations);
		allDestinations.addAll(distantLandmarks);
		
		// Resolve the user input to a destination (exit or landmark)
		Entity matchedDestination = ds.resolveEntity(
			client,
			userInput,
			allDestinations,
			destination -> {
				// For exits, return the exit name
				for (com.benleskey.textengine.model.ConnectionDescriptor desc : exits) {
					if (desc.getTo().equals(destination)) {
						return desc.getExitName();
					}
				}
				// For distant landmarks, return their description
				if (distantLandmarks.contains(destination)) {
					var looks = game.getSystem(com.benleskey.textengine.systems.LookSystem.class)
						.getLooksFromEntity(destination, ws.getCurrentTime());
					if (!looks.isEmpty()) {
						return looks.get(0).getDescription();
					}
				}
				return null;
			}
		);
		
		if (matchedDestination == null) {
			// Ambiguous or no match
			client.sendOutput(CommandOutput.make(M_GO_FAIL)
				.put(M_GO_ERROR, "no_exit")
				.text(Markup.escape("You can't go that way.")));
			return;
		}
		
		// Check if the matched destination is a landmark (not a direct exit)
		boolean isLandmark = distantLandmarks.contains(matchedDestination);
		
		com.benleskey.textengine.model.ConnectionDescriptor matchedExit = null;
		
		if (isLandmark) {
			// User is trying to go to a distant landmark
			// Find the closest adjacent exit that moves toward the landmark
			// For now, we'll just pick the first available exit as a reasonable choice
			// Future: implement proper pathfinding based on spatial distance
			
			if (exits.isEmpty()) {
				client.sendOutput(CommandOutput.make(M_GO_FAIL)
					.put(M_GO_ERROR, "no_exits")
					.text(Markup.escape("You can't move from here.")));
				return;
			}
			
			// Pick first exit as the direction toward the landmark
			matchedExit = exits.get(0);
			
			// (We'll get landmark and destination names later when building the message)
		} else {
			// Find the exit descriptor for the matched destination
			for (com.benleskey.textengine.model.ConnectionDescriptor desc : exits) {
				if (desc.getTo().equals(matchedDestination)) {
					matchedExit = desc;
					break;
				}
			}
		}
		
		// This should never happen since we matched the destination, but check anyway
		if (matchedExit == null) {
			client.sendOutput(CommandOutput.make(M_GO_FAIL)
				.put(M_GO_ERROR, "no_exit")
				.text(Markup.escape("You can't go that way.")));
			return;
		}

		// Always use ProceduralWorldPlugin to handle navigation
		// It will either find existing place or generate new one, and ensure neighbors exist
		ProceduralWorldPlugin worldGen = (ProceduralWorldPlugin) game.getPlugin(ProceduralWorldPlugin.class);
		
		// The matched destination is the place we're navigating to
		// (For landmarks, matchedExit points to a place that moves us toward the landmark)
		Entity destination = matchedExit.getTo();
		worldGen.ensurePlaceHasNeighbors(destination);

		// Move the actor: remove from current location, add to new location
		// We cancel the old containment and create a new one
		var oldContainment = containers.get(0).getRelationship();
		
		// Cancel old relationship
		game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
			.cancelEvent(oldContainment);
		
		// Create new relationship
		rs.add(destination, actor, rs.rvContains);

		// Build the navigation message
		Markup.Safe message;
		
		if (isLandmark) {
			// Navigating toward a distant landmark - show the actual destination and the landmark
			var destinationLooks = game.getSystem(com.benleskey.textengine.systems.LookSystem.class)
				.getLooksFromEntity(destination, ws.getCurrentTime());
			String destinationDesc = !destinationLooks.isEmpty() ? destinationLooks.get(0).getDescription() : "there";
			
			var landmarkLooks = game.getSystem(com.benleskey.textengine.systems.LookSystem.class)
				.getLooksFromEntity(matchedDestination, ws.getCurrentTime());
			String landmarkDesc = !landmarkLooks.isEmpty() ? landmarkLooks.get(0).getDescription() : "the landmark";
			
			message = Markup.concat(
				Markup.raw("You head toward "),
				Markup.em(destinationDesc),
				Markup.raw(", moving closer to "),
				Markup.em(landmarkDesc),
				Markup.raw(".")
			);
		} else {
			// Normal navigation - show the destination
			var destinationLooks = game.getSystem(com.benleskey.textengine.systems.LookSystem.class)
				.getLooksFromEntity(destination, ws.getCurrentTime());
			String destinationDesc = !destinationLooks.isEmpty() ? destinationLooks.get(0).getDescription() : "there";
			
			message = Markup.concat(
				Markup.raw("You go to "),
				Markup.em(destinationDesc),
				Markup.raw(".")
			);
		}

		client.sendOutput(CommandOutput.make(M_GO_SUCCESS)
			.put(M_GO_DESTINATION, destination.getKeyId())
			.put(M_GO_EXIT, matchedExit.getExitName())
			.text(message));
		
		// Automatically look around the new location
		game.feedCommand(client, CommandInput.make(LOOK));
	}
}
