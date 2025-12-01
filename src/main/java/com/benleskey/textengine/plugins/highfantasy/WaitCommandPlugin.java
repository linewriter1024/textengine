package com.benleskey.textengine.plugins.highfantasy;

import com.benleskey.textengine.Client;
import com.benleskey.textengine.Game;
import com.benleskey.textengine.Plugin;
import com.benleskey.textengine.commands.Command;
import com.benleskey.textengine.commands.CommandInput;
import com.benleskey.textengine.commands.CommandOutput;
import com.benleskey.textengine.commands.CommandVariant;
import com.benleskey.textengine.hooks.core.OnCoreSystemsReady;
import com.benleskey.textengine.model.ActionValidation;
import com.benleskey.textengine.model.DTime;
import com.benleskey.textengine.model.Entity;
import com.benleskey.textengine.plugins.core.EntityPlugin;
import com.benleskey.textengine.plugins.procgen1.systems.ProceduralWorldPlugin;
import com.benleskey.textengine.systems.ActionSystem;
import com.benleskey.textengine.util.Markup;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin that implements the wait command.
 * Allows players to advance time deliberately.
 */
public class WaitCommandPlugin extends Plugin implements OnCoreSystemsReady {

	private static final String WAIT = "wait";
	private static final String WAIT_DURATION = "wait_duration";
	private static final String M_WAIT = "wait";
	private static final String M_DURATION = "duration";

	// Error codes
	private static final String ERR_INVALID_DURATION = "invalid_duration";

	// System fields
	private ActionSystem actorActionSystem;

	// Pattern for parsing durations like "1 minute", "2 hours", "30 seconds", "30"
	private static final Pattern DURATION_PATTERN = Pattern.compile(
			"^(\\d+)\\s*(second|seconds|minute|minutes|hour|hours|s|m|h)?$",
			Pattern.CASE_INSENSITIVE);

	public WaitCommandPlugin(Game game) {
		super(game);
	}

	@Override
	public Set<Plugin> getDependencies() {
		return Set.of(game.getPlugin(EntityPlugin.class), game.getPlugin(ProceduralWorldPlugin.class));
	}

	@Override
	public void onCoreSystemsReady() {
		// Initialize systems
		actorActionSystem = game.getSystem(ActionSystem.class);

		game.registerCommand(new Command(WAIT, this::handleWait,
				new CommandVariant(WAIT_DURATION, "^(?:wait)(?:\\s+(.+?))?\\s*$", this::parseWait)));
	}

	private CommandInput parseWait(Matcher matcher) {
		String duration = matcher.group(1);
		return CommandInput.make(WAIT).put(M_DURATION, duration != null ? duration.trim() : "");
	}

	private void handleWait(Client client, CommandInput input) {
		Entity actor = client.getEntity().orElse(null);
		if (actor == null) {
			client.sendOutput(Client.NO_ENTITY);
			return;
		}

		// Default: wait 1 second
		long seconds = 1;

		// Parse duration from input arguments
		String args = input.get(M_DURATION);
		if (args != null && !args.isEmpty()) {
			Matcher m = DURATION_PATTERN.matcher(args);
			if (m.matches()) {
				int amount = Integer.parseInt(m.group(1));
				String unit = m.group(2);

				if (unit == null) {
					// No unit specified - treat as seconds
					seconds = amount;
				} else {
					// Convert based on unit
					switch (unit.toLowerCase()) {
						case "s":
						case "second":
						case "seconds":
							seconds = amount;
							break;
						case "m":
						case "minute":
						case "minutes":
							seconds = amount * 60L;
							break;
						case "h":
						case "hour":
						case "hours":
							seconds = amount * 3600L;
							break;
					}
				}
			} else {
				client.sendOutput(CommandOutput.make(M_WAIT)
						.put(CommandOutput.M_ERROR, ERR_INVALID_DURATION)
						.text(Markup
								.escape("Invalid duration format. Use: wait, wait 30, wait 1 minute, wait 2 hours")));
				return;
			}
		}

		// Queue wait action (time advancement happens inside queueAction for players)
		ActionValidation validation = actorActionSystem.queueAction(
				(com.benleskey.textengine.entities.Actor) actor,
				actorActionSystem.ACTION_WAIT,
				actor, // target is unused for wait action
				DTime.fromSeconds(seconds));

		if (!validation.isValid()) {
			client.sendOutput(validation.getErrorOutput());
			return;
		}

		// Success - action has already broadcast the result to player
	}
}
