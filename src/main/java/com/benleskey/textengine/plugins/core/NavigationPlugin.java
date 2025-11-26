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
import com.benleskey.textengine.systems.ConnectionSystem;
import com.benleskey.textengine.systems.LookSystem;
import com.benleskey.textengine.systems.RelationshipSystem;
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
		
		String exitName = exitOptional.get().toString();
		
		// Normalize common abbreviations
		exitName = normalizeDirection(exitName);

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

		// Always use ProceduralWorldPlugin to handle navigation
		// It will either find existing place or generate new one, and ensure neighbors exist
		ProceduralWorldPlugin worldGen = (ProceduralWorldPlugin) game.getPlugin(ProceduralWorldPlugin.class);
		Entity destination = worldGen.generatePlaceForExit(currentLocation, exitName);

		// Move the actor: remove from current location, add to new location
		// We cancel the old containment and create a new one
		var oldContainment = containers.get(0).getRelationship();
		
		// Cancel old relationship
		game.getSystem(com.benleskey.textengine.systems.EventSystem.class)
			.cancelEvent(oldContainment);
		
		// Create new relationship
		rs.add(destination, actor, rs.rvContains);

		// Get destination description for movement message
		LookSystem ls = game.getSystem(LookSystem.class);
		List<com.benleskey.textengine.model.LookDescriptor> destLooks = 
			ls.getLooksFromEntity(destination, ws.getCurrentTime());
		
		String destDescription = destLooks.isEmpty() 
			? "somewhere" 
			: destLooks.get(0).getDescription();

		// Build safe markup message with proper article handling
		Markup.Safe message = Markup.concat(
			Markup.raw("You go "),
			Markup.em(exitName),
			Markup.raw(destDescription.isEmpty() ? "." : " to "),
			Markup.escape(destDescription),
			Markup.raw(destDescription.isEmpty() ? "" : ".")
		);

		client.sendOutput(CommandOutput.make(M_GO_SUCCESS)
			.put(M_GO_DESTINATION, destination.getKeyId())
			.put(M_GO_EXIT, exitName)
			.text(message));
		
		// Automatically look around the new location
		game.feedCommand(client, CommandInput.make(LOOK));
	}

	/**
	 * Normalize common direction abbreviations.
	 */
	private String normalizeDirection(String direction) {
		return switch (direction) {
			case "n" -> "north";
			case "s" -> "south";
			case "e" -> "east";
			case "w" -> "west";
			case "ne" -> "northeast";
			case "nw" -> "northwest";
			case "se" -> "southeast";
			case "sw" -> "southwest";
			case "u" -> "up";
			case "d" -> "down";
			default -> direction;
		};
	}
}
