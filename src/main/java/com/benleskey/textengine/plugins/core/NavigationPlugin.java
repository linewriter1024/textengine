package com.benleskey.textengine.plugins.core;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.entities.actions.ActionValidation;
import com.benleskey.textengine.hooks.core.OnPluginInitialize;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.systems.ActorActionSystem;
import com.benleskey.textengine.systems.ConnectionSystem;
import com.benleskey.textengine.systems.DisambiguationSystem;
import com.benleskey.textengine.systems.EntityDescriptionSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
import com.benleskey.textengine.systems.SpatialSystem;
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
	
	// Error codes
	public static final String ERR_NO_DIRECTION = "no_direction";
	public static final String ERR_PLAYER_NOWHERE = "player_nowhere";
	public static final String ERR_NO_EXIT = "no_exit";
	public static final String ERR_NO_EXITS = "no_exits";


	public NavigationPlugin(Game game) {
		super(game);
	}

	@Override
	public void onPluginInitialize() {
		game.registerCommand(new Command(GO, this::handleGo,
			// Match: go north, go n, go castle, go #1234 (entity ID), etc.
			// Accepts both destination names and entity IDs with # prefix
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
				.put(CommandOutput.M_ERROR, ERR_NO_DIRECTION)
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
				.put(CommandOutput.M_ERROR, ERR_PLAYER_NOWHERE)
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
	EntityDescriptionSystem eds = game.getSystem(EntityDescriptionSystem.class);
	DisambiguationSystem.ResolutionResult<Entity> result = ds.resolveEntityWithAmbiguity(
		client,
		userInput,
		allDestinations,
		destination -> eds.getSimpleDescription(destination, ws.getCurrentTime())
	);
	
	if (result.isNotFound()) {
		client.sendOutput(CommandOutput.make(M_GO_FAIL)
			.put(CommandOutput.M_ERROR, ERR_NO_EXIT)
			.text(Markup.escape("You can't go that way.")));
		return;
	}
	
	if (result.isAmbiguous()) {
		// Multiple matches - show disambiguation
		ds.sendDisambiguationPrompt(
			client,
			M_GO_FAIL,
			userInput,
			result.getAmbiguousMatches(),
			destination -> eds.getSimpleDescription(destination, ws.getCurrentTime())
		);
		return;
	}
	
	Entity matchedDestination = result.getUniqueMatch();		// Check if the matched destination is a landmark (not a direct exit)
		boolean isLandmark = distantLandmarks.contains(matchedDestination);
		
		com.benleskey.textengine.model.ConnectionDescriptor matchedExit = null;
		
		if (isLandmark) {
			// User is trying to go to a distant landmark
			// Find the adjacent exit that moves closest toward the landmark using spatial pathfinding
			
			if (exits.isEmpty()) {
				client.sendOutput(CommandOutput.make(M_GO_FAIL)
					.put(CommandOutput.M_ERROR, ERR_NO_EXITS)
					.text(Markup.escape("You can't move from here.")));
				return;
			}
			
			// Use SpatialSystem to find the exit that moves us closest to the landmark at continent scale
			SpatialSystem spatialSystem = game.getSystem(SpatialSystem.class);
			
			// Find which exit destination is closest to the landmark
			Entity closestDestination = spatialSystem.findClosestToTarget(
				SpatialSystem.SCALE_CONTINENT, exitDestinations, matchedDestination);
			
			if (closestDestination != null) {
				// Find the exit descriptor for the closest destination
				for (com.benleskey.textengine.model.ConnectionDescriptor exit : exits) {
					if (exit.getTo().equals(closestDestination)) {
						matchedExit = exit;
						break;
					}
				}
			}
			
			// Fallback: pick first exit if pathfinding failed
			if (matchedExit == null) {
				matchedExit = exits.get(0);
			}
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
				.put(CommandOutput.M_ERROR, ERR_NO_EXIT)
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

		// Use ActorActionSystem to queue the move action
		ActorActionSystem aas = game.getSystem(ActorActionSystem.class);
		DTime moveTime = DTime.fromSeconds(60);
		
		// Queue the action (validation + execution happens inside for players)
		ActionValidation validation = aas.queueAction((com.benleskey.textengine.entities.Actor) actor, aas.ACTION_MOVE, destination, moveTime);
		
		if (!validation.isValid()) {
			client.sendOutput(validation.getErrorOutput());
			return;
		}
		
		// Success - action has already broadcast the result to player
		
	// For pathfinding toward landmarks, send context message (not a broadcast)
	if (isLandmark) {
		String destinationDesc = eds.getSimpleDescription(destination, ws.getCurrentTime(), "there");
		String landmarkDesc = eds.getSimpleDescription(matchedDestination, ws.getCurrentTime(), "the landmark");			client.sendOutput(CommandOutput.make("navigation_context")
				.put("destination", destinationDesc)
				.put("landmark", landmarkDesc)
				.text(Markup.concat(
					Markup.raw("You head toward "),
					Markup.em(destinationDesc),
					Markup.raw(", moving closer to "),
					Markup.em(landmarkDesc),
					Markup.raw(".")
				)));
		}
		
		// Automatically look around the new location
		game.feedCommand(client, CommandInput.make(LOOK));
	}
}
